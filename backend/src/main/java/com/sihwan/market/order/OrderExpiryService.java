package com.sihwan.market.order;

import com.sihwan.market.product.StockReservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderExpiryService {

    private final OrderMapper orders;
    private final TransactionTemplate tx;
    private final int expireMinutes;

    private final StockReservation reservation;

    public OrderExpiryService(OrderMapper orders, TransactionTemplate tx, StockReservation reservation,
                              @Value("${app.order.expire-minutes}") int expireMinutes) {
        this.reservation = reservation;
        this.orders = orders;
        this.tx = tx;
        this.expireMinutes = expireMinutes;
    }

    @Scheduled(fixedDelayString = "${app.order.expire-check-ms}")
    public void expireScheduled() {
        expire(expireMinutes);
    }

    public int expire(int minutes) {
        int canceled = 0;
        for (long id : orders.findExpiredIds(minutes)) {
            Boolean done = tx.execute(s -> {
                if (orders.cancel(id) == 0) {
                    return false;
                }
                orders.restoreStock(id);
                orders.findProductIds(id).forEach(reservation::invalidateAfterCommit);
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                canceled++;
            }
        }
        return canceled;
    }
}
