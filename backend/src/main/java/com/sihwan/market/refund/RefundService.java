package com.sihwan.market.refund;

import com.sihwan.market.order.Order;
import com.sihwan.market.order.OrderItem;
import com.sihwan.market.order.OrderMapper;
import com.sihwan.market.payment.Payment;
import com.sihwan.market.payment.PaymentMapper;
import com.sihwan.market.payment.PortOneClient;
import com.sihwan.market.product.StockReservation;
import com.sihwan.market.refund.RefundDtos.RefundRequest;
import com.sihwan.market.refund.RefundDtos.RefundResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefundService {

    private static final String DEFAULT_REASON = "구매자 환불 요청";

    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final RefundMapper refunds;
    private final PortOneClient portOne;
    private final TransactionTemplate tx;
    private final StockReservation reservation;

    public RefundService(OrderMapper orders, PaymentMapper payments, RefundMapper refunds,
                         PortOneClient portOne, TransactionTemplate tx, StockReservation reservation) {
        this.reservation = reservation;
        this.orders = orders;
        this.payments = payments;
        this.refunds = refunds;
        this.portOne = portOne;
        this.tx = tx;
    }

    public RefundResponse refund(long buyerId, long orderId, RefundRequest req) {
        Order order = orders.findById(orderId)
                .filter(o -> o.buyerId() == buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문이 없습니다"));
        if (!"PAID".equals(order.status()) && !"PARTIAL_REFUNDED".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "환불할 수 없는 주문 상태입니다");
        }
        OrderItem item = orders.findItem(orderId, req.orderItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문 항목이 없습니다"));
        Payment payment = payments.findPaidByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "환불할 결제가 없습니다"));
        String reason = req.reason() == null || req.reason().isBlank() ? DEFAULT_REASON : req.reason();

        long refundId;
        try {
            refundId = refunds.insertRequested(payment.id(), item.id(), item.amount(), reason);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 환불 처리된 항목입니다");
        }

        try {
            portOne.cancel(payment.portonePaymentId(), item.amount(), reason);
        } catch (ResponseStatusException e) {
            // 거절(409)은 취소되지 않은 것이 확실하다. 서버 오류, 시간 초과는 취소됐는지 모르므로
            // REQUESTED로 두고 복구 배치가 PortOne 상태를 보고 정리한다 (실패로 적으면 재시도로 이중 환불될 수 있다)
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                refunds.markFailed(refundId);
            }
            throw e;
        }

        String orderStatus = complete(refundId, orderId, payment.id(), item);
        if (orderStatus == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 환불 처리된 항목입니다");
        }
        return new RefundResponse(refundId, orderStatus);
    }

    /** 취소가 확인된 환불을 반영한다. 이미 처리된 환불이면 아무것도 하지 않고 null (재고가 두 번 늘지 않게). */
    String complete(long refundId, long orderId, long paymentId, OrderItem item) {
        return tx.execute(s -> {
            orders.lockById(orderId);
            if (refunds.markCompleted(refundId) == 0) {
                return null;
            }
            orders.increaseStock(item.productId(), item.quantity());
            reservation.invalidateAfterCommit(item.productId());
            boolean all = refunds.countCompleted(orderId) >= orders.countItems(orderId);
            String status = all ? "REFUNDED" : "PARTIAL_REFUNDED";
            orders.updateRefundStatus(orderId, status);
            if (all) {
                payments.markCanceled(paymentId);
            }
            return status;
        });
    }
}
