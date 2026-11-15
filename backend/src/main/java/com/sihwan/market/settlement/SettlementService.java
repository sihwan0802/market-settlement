package com.sihwan.market.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SettlementMapper mapper;
    private final TransactionTemplate tx;

    public SettlementService(SettlementMapper mapper, TransactionTemplate tx) {
        this.mapper = mapper;
        this.tx = tx;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void settleYesterday() {
        settle(LocalDate.now(KST).minusDays(1));
        List<Long> bad = findInconsistent();
        if (!bad.isEmpty()) {
            log.error("정산 정합성 오류: settlement id {}", bad);
        }
    }

    public List<Long> findInconsistent() {
        return mapper.findInconsistentIds();
    }

    /** 해당 날짜(KST)의 판매자별 정산을 만든다. 이미 있는 판매자는 건너뛰므로 다시 실행해도 안전하다. */
    public int settle(LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime to = date.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        int created = 0;
        for (long sellerId : mapper.findSellerIds(from, to)) {
            if (Boolean.TRUE.equals(tx.execute(s -> settleSeller(sellerId, date, from, to)))) {
                created++;
            }
        }
        return created;
    }

    private boolean settleSeller(long sellerId, LocalDate date, OffsetDateTime from, OffsetDateTime to) {
        List<SettlementLine> sales = mapper.findSales(sellerId, from, to);
        List<SettlementLine> refunds = mapper.findRefunds(sellerId, from, to);
        long gross = sales.stream().mapToLong(SettlementLine::amount).sum();
        long refund = refunds.stream().mapToLong(SettlementLine::amount).sum();
        BigDecimal feeRate = mapper.findFeeRate(sellerId);
        long fee = BigDecimal.valueOf(gross - refund).multiply(feeRate)
                .setScale(0, RoundingMode.FLOOR).longValueExact();
        Long id = mapper.insertIfAbsent(sellerId, date, gross, refund, feeRate, fee, gross - refund - fee);
        if (id == null) {
            return false;
        }
        sales.forEach(l -> mapper.insertItem(id, "SALE", l.id(), l.amount()));
        refunds.forEach(l -> mapper.insertItem(id, "REFUND", l.id(), -l.amount()));
        return true;
    }
}
