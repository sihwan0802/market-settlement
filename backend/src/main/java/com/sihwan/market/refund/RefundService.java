package com.sihwan.market.refund;

import com.sihwan.market.order.Order;
import com.sihwan.market.order.OrderItem;
import com.sihwan.market.order.OrderMapper;
import com.sihwan.market.payment.Payment;
import com.sihwan.market.payment.PaymentMapper;
import com.sihwan.market.payment.PortOneClient;
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

    public RefundService(OrderMapper orders, PaymentMapper payments, RefundMapper refunds,
                         PortOneClient portOne, TransactionTemplate tx) {
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
        } catch (RuntimeException e) {
            refunds.markFailed(refundId);
            throw e;
        }

        String orderStatus = tx.execute(s -> {
            orders.lockById(orderId);
            refunds.markCompleted(refundId);
            orders.increaseStock(item.productId(), item.quantity());
            boolean all = refunds.countCompleted(orderId) >= orders.countItems(orderId);
            String status = all ? "REFUNDED" : "PARTIAL_REFUNDED";
            orders.updateRefundStatus(orderId, status);
            if (all) {
                payments.markCanceled(payment.id());
            }
            return status;
        });
        return new RefundResponse(refundId, orderStatus);
    }
}
