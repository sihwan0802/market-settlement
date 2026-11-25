package com.sihwan.market.payment;

import com.sihwan.market.order.Order;
import com.sihwan.market.order.OrderMapper;
import com.sihwan.market.payment.PaymentDtos.ConfirmResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private static final Pattern PAYMENT_ID = Pattern.compile("^order-([0-9]{1,18})-");

    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final PortOneClient portOne;
    private final TransactionTemplate tx;

    public PaymentService(OrderMapper orders, PaymentMapper payments, PortOneClient portOne, TransactionTemplate tx) {
        this.orders = orders;
        this.payments = payments;
        this.portOne = portOne;
        this.tx = tx;
    }

    public ConfirmResponse confirm(long buyerId, long orderId, String paymentId) {
        Order order = orders.findById(orderId)
                .filter(o -> o.buyerId() == buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문이 없습니다"));
        if (!paymentId.startsWith("order-" + orderId + "-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주문과 맞지 않는 결제 번호입니다");
        }
        return settle(order, orderId, paymentId);
    }

    /**
     * 웹훅이 알려 준 결제를 PortOne에 다시 조회해 반영한다 (프론트 호출 없이도 주문이 PAID가 되게).
     * 이미 처리한 결제는 같은 결과로 건너뛰고, 금액이 다르면 예외(4xx), 조회 실패는 예외(5xx)로 PortOne이 다시 보내게 한다.
     */
    public String syncPaid(String paymentId) {
        Matcher m = PAYMENT_ID.matcher(paymentId);
        if (!m.find()) {
            return "IGNORED"; // 우리가 만든 결제 번호가 아니다
        }
        long orderId = Long.parseLong(m.group(1));
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            return "IGNORED";
        }
        try {
            settle(order, orderId, paymentId);
            return "PAID";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                return "CONFLICT"; // 아직 결제 전이거나 결제할 수 없는 주문이라 취소했다. 다시 보내도 같은 결과
            }
            throw e;
        }
    }

    private ConfirmResponse settle(Order order, long orderId, String paymentId) {
        if (payments.findByPortoneId(paymentId).isPresent()) {
            return alreadyProcessed(orderId, paymentId);
        }

        // 외부 호출은 트랜잭션 밖에서 한다. DB 커넥션을 잡은 채 네트워크를 기다리지 않기 위함
        PortOnePayment paid = portOne.get(paymentId);
        if (!"PAID".equals(paid.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제가 완료되지 않았습니다");
        }
        if (!"CREATED".equals(order.status())) {
            // 만료되었거나 이미 결제된 주문에 돈이 빠져나간 경우. 그대로 두면 고객 돈만 묶이므로 전액 취소한다
            return cancelUnusable(orderId, paymentId, paid.amount().total());
        }
        if (!"KRW".equals(paid.currency()) || paid.amount().total() != order.totalAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 다릅니다");
        }

        try {
            boolean ok = Boolean.TRUE.equals(tx.execute(s -> {
                payments.insertPaid(orderId, paymentId, order.totalAmount());
                if (orders.markPaid(orderId) == 0) {
                    // 확인하는 사이 주문이 만료된 경우
                    s.setRollbackOnly();
                    return false;
                }
                return true;
            }));
            if (!ok) {
                return cancelUnusable(orderId, paymentId, paid.amount().total());
            }
        } catch (DuplicateKeyException e) {
            return alreadyProcessed(orderId, paymentId);
        }
        return new ConfirmResponse(orderId, "PAID");
    }

    private ConfirmResponse cancelUnusable(long orderId, String paymentId, long amount) {
        try {
            payments.insertCancelRequested(orderId, paymentId, amount);
        } catch (DuplicateKeyException e) {
            return alreadyProcessed(orderId, paymentId);
        }
        return cancelAndReject(payments.findByPortoneId(paymentId).orElseThrow());
    }

    private ConfirmResponse cancelAndReject(Payment payment) {
        portOne.cancel(payment.portonePaymentId(), payment.amount(), "주문을 결제할 수 없어 자동 취소");
        payments.markCanceled(payment.id());
        throw new ResponseStatusException(HttpStatus.CONFLICT, "결제할 수 없는 주문이라 결제가 취소되었습니다");
    }

    private ConfirmResponse alreadyProcessed(long orderId, String paymentId) {
        Payment existing = payments.findByPortoneId(paymentId).orElseThrow();
        if (existing.orderId() != orderId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용된 결제 번호입니다");
        }
        if ("CANCEL_REQUESTED".equals(existing.status())) {
            return cancelAndReject(existing); // 취소 요청이 실패했던 건을 다시 시도
        }
        if ("CANCELED".equals(existing.status()) && !payments.hasPaidAt(existing.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제할 수 없는 주문이라 결제가 취소되었습니다");
        }
        return new ConfirmResponse(orderId, "PAID");
    }
}
