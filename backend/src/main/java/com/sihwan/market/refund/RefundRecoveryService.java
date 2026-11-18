package com.sihwan.market.refund;

import com.sihwan.market.order.OrderItem;
import com.sihwan.market.order.OrderMapper;
import com.sihwan.market.payment.PortOneClient;
import com.sihwan.market.payment.PortOnePayment;
import com.sihwan.market.refund.RefundMapper.Stuck;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 오래 REQUESTED로 남은 환불을 PortOne 상태와 맞춘다.
 * 취소 요청 뒤 서버가 죽었거나 응답을 못 받은 경우다.
 */
@Service
public class RefundRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RefundRecoveryService.class);

    private final RefundMapper refunds;
    private final OrderMapper orders;
    private final PortOneClient portOne;
    private final RefundService refundService;
    private final int recoverAfterMinutes;

    public RefundRecoveryService(RefundMapper refunds, OrderMapper orders, PortOneClient portOne,
                                 RefundService refundService,
                                 @Value("${app.refund.recover-after-minutes}") int recoverAfterMinutes) {
        this.refunds = refunds;
        this.orders = orders;
        this.portOne = portOne;
        this.refundService = refundService;
        this.recoverAfterMinutes = recoverAfterMinutes;
    }

    @Scheduled(fixedDelayString = "${app.refund.recover-check-ms}")
    public void recoverScheduled() {
        recover(recoverAfterMinutes);
    }

    /** 처리한 환불 수를 돌려준다. */
    public int recover(int minutes) {
        int handled = 0;
        List<Stuck> stuck = refunds.findStuck(minutes);
        int i = 0;
        while (i < stuck.size()) {
            long paymentId = stuck.get(i).paymentId();
            int end = i;
            while (end < stuck.size() && stuck.get(end).paymentId() == paymentId) {
                end++;
            }
            handled += recoverPayment(stuck.subList(i, end));
            i = end;
        }
        return handled;
    }

    // PortOne이 알려주는 취소 합계는 누적값이다. DB에 완료로 반영된 합계를 뺀 만큼이 아직 반영 안 된 취소분이다.
    private int recoverPayment(List<Stuck> group) {
        Stuck first = group.get(0);
        long available;
        try {
            PortOnePayment p = portOne.get(first.portonePaymentId());
            available = p.amount().cancelled() - refunds.sumCompleted(first.paymentId());
        } catch (RuntimeException e) {
            log.warn("환불 복구: 결제 {} 조회 실패, 다음에 다시 시도: {}", first.portonePaymentId(), e.toString());
            return 0;
        }
        int handled = 0;
        for (Stuck r : group) {
            if (available >= r.amount()) {
                OrderItem item = orders.findItem(r.orderId(), r.orderItemId()).orElseThrow();
                refundService.complete(r.id(), r.orderId(), r.paymentId(), item);
                available -= r.amount();
                log.info("환불 복구: {} 취소 확인, 완료 처리", r.id());
            } else {
                refunds.markFailed(r.id());
                log.info("환불 복구: {} 취소되지 않아 실패 처리", r.id());
            }
            handled++;
        }
        return handled;
    }
}
