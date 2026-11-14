package com.sihwan.market.payment;

import com.sihwan.market.order.Order;
import com.sihwan.market.order.OrderMapper;
import com.sihwan.market.payment.PaymentDtos.ConfirmResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

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

        if (payments.findByPortoneId(paymentId).isPresent()) {
            return alreadyProcessed(orderId, paymentId);
        }
        if (!"CREATED".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제할 수 없는 주문 상태입니다");
        }

        // 외부 호출은 트랜잭션 밖에서 한다. DB 커넥션을 잡은 채 네트워크를 기다리지 않기 위함
        PortOnePayment paid = portOne.get(paymentId);
        if (!"PAID".equals(paid.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제가 완료되지 않았습니다");
        }
        if (!"KRW".equals(paid.currency()) || paid.amount().total() != order.totalAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 다릅니다");
        }

        try {
            tx.executeWithoutResult(s -> {
                payments.insertPaid(orderId, paymentId, order.totalAmount());
                if (orders.markPaid(orderId) == 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "결제할 수 없는 주문 상태입니다");
                }
            });
        } catch (DuplicateKeyException e) {
            return alreadyProcessed(orderId, paymentId);
        }
        return new ConfirmResponse(orderId, "PAID");
    }

    private ConfirmResponse alreadyProcessed(long orderId, String paymentId) {
        Payment existing = payments.findByPortoneId(paymentId).orElseThrow();
        if (existing.orderId() != orderId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용된 결제 번호입니다");
        }
        return new ConfirmResponse(orderId, "PAID");
    }
}
