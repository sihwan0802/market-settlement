package com.sihwan.market.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
class SettlementServiceTest {

    // 다른 테스트 데이터와 겹치지 않도록 과거 날짜를 쓴다
    private static final LocalDate DAY1 = LocalDate.of(2020, 1, 15);
    private static final LocalDate DAY2 = LocalDate.of(2020, 1, 16);

    @Autowired SettlementService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    private long seller(String feeRate) {
        long member = jdbc.queryForObject("INSERT INTO member (email, password_hash, name, role) "
                + "VALUES (?, 'x', 's', 'SELLER') RETURNING id", Long.class, UUID.randomUUID() + "@t.com");
        return jdbc.queryForObject("INSERT INTO seller (member_id, store_name, fee_rate) "
                + "VALUES (?, 's', ?::numeric) RETURNING id", Long.class, member, feeRate);
    }

    private long product(long sellerId) {
        return jdbc.queryForObject("INSERT INTO product (seller_id, name, price) VALUES (?, 'p', 1000) RETURNING id",
                Long.class, sellerId);
    }

    /** 결제 완료 주문 1건(항목 1개). 항목 id와 결제 id를 돌려준다. */
    private long[] paidItem(long sellerId, long productId, long amount, String paidAt) {
        long buyer = jdbc.queryForObject("INSERT INTO member (email, password_hash, name, role) "
                + "VALUES (?, 'x', 'b', 'BUYER') RETURNING id", Long.class, UUID.randomUUID() + "@t.com");
        long order = jdbc.queryForObject("INSERT INTO orders (buyer_id, status, total_amount) "
                + "VALUES (?, 'PAID', ?) RETURNING id", Long.class, buyer, amount);
        long item = jdbc.queryForObject("INSERT INTO order_item (order_id, product_id, seller_id, unit_price, quantity, amount) "
                + "VALUES (?, ?, ?, ?, 1, ?) RETURNING id", Long.class, order, productId, sellerId, amount, amount);
        long payment = jdbc.queryForObject("INSERT INTO payment (order_id, idempotency_key, amount, status, paid_at) "
                + "VALUES (?, ?, ?, 'PAID', ?::timestamptz) RETURNING id", Long.class,
                order, UUID.randomUUID().toString(), amount, paidAt);
        return new long[] {item, payment};
    }

    private void refund(long[] paidItem, long amount, String refundedAt) {
        jdbc.update("INSERT INTO refund (payment_id, order_item_id, amount, status, refunded_at) "
                + "VALUES (?, ?, ?, 'COMPLETED', ?::timestamptz)", paidItem[1], paidItem[0], amount, refundedAt);
    }

    private Map<String, Object> settlement(long sellerId, LocalDate date) {
        return jdbc.queryForMap("SELECT * FROM settlement WHERE seller_id = ? AND settle_date = ?", sellerId, date);
    }

    @Test
    void settles_sales_minus_refunds_minus_fee_per_seller_and_is_idempotent() {
        long a = seller("0.1000");
        long b = seller("0.0500");
        long pa = product(a);
        long pb = product(b);

        long[] a1 = paidItem(a, pa, 10000, "2020-01-15 00:00:00+09");
        long[] a2 = paidItem(a, pa, 5001, "2020-01-15 23:59:59+09");
        paidItem(a, pa, 7000, "2020-01-16 00:00:00+09");            // 다음 날 매출: 제외
        refund(a2, 5001, "2020-01-15 12:00:00+09");

        long[] b1 = paidItem(b, pb, 20000, "2020-01-15 10:00:00+09");
        refund(b1, 20000, "2020-01-16 09:00:00+09");                 // 환불은 환불한 날 정산에서 차감

        assertEquals(2, service.settle(DAY1));
        Map<String, Object> sa = settlement(a, DAY1);
        assertEquals(15001L, sa.get("gross_sales"));
        assertEquals(5001L, sa.get("refund_amount"));
        assertEquals(1000L, sa.get("fee_amount"));                   // floor(10000 * 0.1)
        assertEquals(9000L, sa.get("net_amount"));
        assertEquals(20000L, settlement(b, DAY1).get("gross_sales"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM settlement_item WHERE settlement_id = ? "
                + "AND source_type = 'SALE'", Integer.class, sa.get("id")));
        assertEquals(-5001L, jdbc.queryForObject("SELECT amount FROM settlement_item WHERE settlement_id = ? "
                + "AND source_type = 'REFUND'", Long.class, sa.get("id")));

        assertTrue(Collections.disjoint(service.findInconsistent(),
                List.of(((Number) sa.get("id")).longValue(), ((Number) settlement(b, DAY1).get("id")).longValue())));

        assertEquals(0, service.settle(DAY1));                       // 재실행: 중복 없음
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settlement WHERE seller_id = ?", Integer.class, a));

        assertEquals(2, service.settle(DAY2));                       // a는 매출 7000, b는 환불만
        Map<String, Object> sb2 = settlement(b, DAY2);
        assertEquals(0L, sb2.get("gross_sales"));
        assertEquals(20000L, sb2.get("refund_amount"));
        assertEquals(-1000L, sb2.get("fee_amount"));                 // floor(-20000 * 0.05)
        assertEquals(-19000L, sb2.get("net_amount"));
    }

    @Test
    void detects_settlements_that_no_longer_match_the_source_data() {
        LocalDate day = LocalDate.of(2020, 2, 10);
        long s1 = seller("0.1000");
        long s2 = seller("0.1000");
        long s3 = seller("0.1000");
        long[] i1 = paidItem(s1, product(s1), 10000, "2020-02-10 10:00:00+09");
        long[] i2 = paidItem(s2, product(s2), 10000, "2020-02-10 10:00:00+09");
        paidItem(s3, product(s3), 10000, "2020-02-10 10:00:00+09");
        service.settle(day);

        long id1 = ((Number) settlement(s1, day).get("id")).longValue();
        long id2 = ((Number) settlement(s2, day).get("id")).longValue();
        long id3 = ((Number) settlement(s3, day).get("id")).longValue();
        assertTrue(!service.findInconsistent().contains(id1));

        refund(i1, 3000, "2020-02-10 20:00:00+09");                  // 정산 뒤에 같은 날짜로 환불이 끼어듦
        jdbc.update("UPDATE settlement SET net_amount = net_amount + 1 WHERE id = ?", id2);   // 금액 변조
        jdbc.update("DELETE FROM settlement_item WHERE settlement_id = ?", id3);              // 명세 누락

        List<Long> bad = service.findInconsistent();
        assertTrue(bad.containsAll(List.of(id1, id2, id3)));
    }

    @Test
    void finds_seller_days_with_activity_but_no_settlement() {
        LocalDate d1 = LocalDate.of(2020, 3, 10);
        LocalDate d2 = LocalDate.of(2020, 3, 11);
        long s1 = seller("0.1000");
        long s2 = seller("0.1000");
        long[] i1 = paidItem(s1, product(s1), 10000, "2020-03-10 10:00:00+09");
        paidItem(s2, product(s2), 8000, "2020-03-10 11:00:00+09");
        refund(i1, 1000, "2020-03-11 09:00:00+09");     // s1은 11일에 환불만 있음

        assertEquals(List.of(new MissingSettlement(s1, d1), new MissingSettlement(s2, d1), new MissingSettlement(s1, d2)),
                service.findMissing(d1, d2).stream().filter(m -> m.sellerId() == s1 || m.sellerId() == s2).toList());

        service.settle(d1);                               // 10일만 정산
        assertEquals(List.of(new MissingSettlement(s1, d2)),
                service.findMissing(d1, d2).stream().filter(m -> m.sellerId() == s1 || m.sellerId() == s2).toList());

        service.settle(d2);
        assertTrue(service.findMissing(d1, d2).stream().noneMatch(m -> m.sellerId() == s1 || m.sellerId() == s2));
    }

    @Test
    void deleted_settlement_row_is_reported_as_missing() {
        LocalDate day = LocalDate.of(2020, 3, 20);
        long s = seller("0.1000");
        paidItem(s, product(s), 5000, "2020-03-20 10:00:00+09");
        service.settle(day);
        assertTrue(service.findMissing(day, day).stream().noneMatch(m -> m.sellerId() == s));

        long id = ((Number) settlement(s, day).get("id")).longValue();
        jdbc.update("DELETE FROM settlement_item WHERE settlement_id = ?", id);
        jdbc.update("DELETE FROM settlement WHERE id = ?", id);

        assertEquals(List.of(new MissingSettlement(s, day)),
                service.findMissing(day, day).stream().filter(m -> m.sellerId() == s).toList());
        assertEquals(1, service.settle(day));           // 다시 정산하면 복구된다
    }

    @Test
    void same_date_cannot_run_twice_at_once_and_lock_is_released_after() {
        LocalDate day = LocalDate.of(2020, 3, 25);
        long s = seller("0.1000");
        paidItem(s, product(s), 5000, "2020-03-25 10:00:00+09");

        redis.opsForValue().set("lock:settlement:" + day, "other-server");   // 다른 서버가 실행 중
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.settle(day));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("other-server", redis.opsForValue().get("lock:settlement:" + day)); // 남의 락은 건드리지 않는다

        redis.delete("lock:settlement:" + day);
        assertEquals(1, service.settle(day));
        assertEquals(null, redis.opsForValue().get("lock:settlement:" + day));          // 끝나면 풀린다
    }
}
