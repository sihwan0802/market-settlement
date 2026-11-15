package com.sihwan.market.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
class SettlementServiceTest {

    // 다른 테스트 데이터와 겹치지 않도록 과거 날짜를 쓴다
    private static final LocalDate DAY1 = LocalDate.of(2020, 1, 15);
    private static final LocalDate DAY2 = LocalDate.of(2020, 1, 16);

    @Autowired SettlementService service;
    @Autowired JdbcTemplate jdbc;

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

        assertEquals(0, service.settle(DAY1));                       // 재실행: 중복 없음
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settlement WHERE seller_id = ?", Integer.class, a));

        assertEquals(2, service.settle(DAY2));                       // a는 매출 7000, b는 환불만
        Map<String, Object> sb2 = settlement(b, DAY2);
        assertEquals(0L, sb2.get("gross_sales"));
        assertEquals(20000L, sb2.get("refund_amount"));
        assertEquals(-1000L, sb2.get("fee_amount"));                 // floor(-20000 * 0.05)
        assertEquals(-19000L, sb2.get("net_amount"));
    }
}
