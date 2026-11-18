package com.sihwan.market.settlement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SettlementMapper {

    // 하루치 판매와 환불을 판매자 구분 없이 한 번에 가져온다
    @Select("SELECT oi.seller_id, oi.id, oi.amount FROM order_item oi JOIN payment p ON p.order_id = oi.order_id "
            + "WHERE p.status IN ('PAID', 'CANCELED') AND p.paid_at >= #{from} AND p.paid_at < #{to} ORDER BY oi.id")
    List<SellerLine> findSales(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Select("SELECT oi.seller_id, r.id, r.amount FROM refund r JOIN order_item oi ON oi.id = r.order_item_id "
            + "WHERE r.status = 'COMPLETED' AND r.refunded_at >= #{from} AND r.refunded_at < #{to} ORDER BY r.id")
    List<SellerLine> findRefunds(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Select("SELECT id, fee_rate FROM seller")
    List<SellerFee> findFeeRates();

    // 이미 정산된 (판매자, 날짜)면 null을 돌려준다
    @Select("INSERT INTO settlement (seller_id, settle_date, gross_sales, refund_amount, fee_rate, fee_amount, net_amount) "
            + "VALUES (#{sellerId}, #{date}, #{gross}, #{refund}, #{feeRate}, #{fee}, #{net}) "
            + "ON CONFLICT (seller_id, settle_date) DO NOTHING RETURNING id")
    Long insertIfAbsent(@Param("sellerId") long sellerId, @Param("date") java.time.LocalDate date,
                        @Param("gross") long gross, @Param("refund") long refund,
                        @Param("feeRate") BigDecimal feeRate, @Param("fee") long fee, @Param("net") long net);

    @Update("INSERT INTO settlement_item (settlement_id, source_type, source_id, amount) "
            + "VALUES (#{settlementId}, #{type}, #{sourceId}, #{amount})")
    void insertItem(@Param("settlementId") long settlementId, @Param("type") String type,
                    @Param("sourceId") long sourceId, @Param("amount") long amount);

    // 저장된 정산을 원천(주문 항목, 환불)에서 다시 계산한 값과 대조해 어긋난 정산 id를 돌려준다.
    // 정산마다 원천을 다시 훑지 않고 (판매자, 날짜)별 합계를 한 번만 만들어 붙인다
    @Select("WITH sales AS (SELECT oi.seller_id, (p.paid_at AT TIME ZONE 'Asia/Seoul')::date AS d, SUM(oi.amount) AS amt "
            + "  FROM order_item oi JOIN payment p ON p.order_id = oi.order_id "
            + "  WHERE p.status IN ('PAID', 'CANCELED') GROUP BY 1, 2), "
            + "refunds AS (SELECT oi.seller_id, (r.refunded_at AT TIME ZONE 'Asia/Seoul')::date AS d, SUM(r.amount) AS amt "
            + "  FROM refund r JOIN order_item oi ON oi.id = r.order_item_id "
            + "  WHERE r.status = 'COMPLETED' GROUP BY 1, 2), "
            + "items AS (SELECT settlement_id, SUM(amount) AS amt FROM settlement_item GROUP BY 1) "
            + "SELECT s.id FROM settlement s "
            + "LEFT JOIN sales sa ON sa.seller_id = s.seller_id AND sa.d = s.settle_date "
            + "LEFT JOIN refunds rf ON rf.seller_id = s.seller_id AND rf.d = s.settle_date "
            + "LEFT JOIN items i ON i.settlement_id = s.id "
            + "WHERE s.net_amount + s.fee_amount <> s.gross_sales - s.refund_amount "
            + "OR COALESCE(i.amt, 0) <> s.gross_sales - s.refund_amount "
            + "OR s.gross_sales <> COALESCE(sa.amt, 0) "
            + "OR s.refund_amount <> COALESCE(rf.amt, 0) "
            + "ORDER BY s.id")
    List<Long> findInconsistentIds();

    // 날짜는 KST 기준. 정산 행이 통째로 빠진 경우를 찾는다
    @Select("SELECT a.seller_id, a.d AS date FROM ("
            + " SELECT oi.seller_id, (p.paid_at AT TIME ZONE 'Asia/Seoul')::date AS d "
            + " FROM order_item oi JOIN payment p ON p.order_id = oi.order_id "
            + " WHERE p.status IN ('PAID', 'CANCELED') AND p.paid_at >= #{from} AND p.paid_at < #{to} "
            + " UNION "
            + " SELECT oi.seller_id, (r.refunded_at AT TIME ZONE 'Asia/Seoul')::date "
            + " FROM refund r JOIN order_item oi ON oi.id = r.order_item_id "
            + " WHERE r.status = 'COMPLETED' AND r.refunded_at >= #{from} AND r.refunded_at < #{to}"
            + ") a WHERE NOT EXISTS (SELECT 1 FROM settlement s WHERE s.seller_id = a.seller_id AND s.settle_date = a.d) "
            + "ORDER BY a.d, a.seller_id")
    List<MissingSettlement> findMissing(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
