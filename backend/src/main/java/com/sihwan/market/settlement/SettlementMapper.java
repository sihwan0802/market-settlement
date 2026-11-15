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

    @Select("SELECT oi.seller_id FROM order_item oi JOIN payment p ON p.order_id = oi.order_id "
            + "WHERE p.status IN ('PAID', 'CANCELED') AND p.paid_at >= #{from} AND p.paid_at < #{to} "
            + "UNION "
            + "SELECT oi.seller_id FROM refund r JOIN order_item oi ON oi.id = r.order_item_id "
            + "WHERE r.status = 'COMPLETED' AND r.refunded_at >= #{from} AND r.refunded_at < #{to}")
    List<Long> findSellerIds(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Select("SELECT oi.id, oi.amount FROM order_item oi JOIN payment p ON p.order_id = oi.order_id "
            + "WHERE oi.seller_id = #{sellerId} AND p.status IN ('PAID', 'CANCELED') "
            + "AND p.paid_at >= #{from} AND p.paid_at < #{to} ORDER BY oi.id")
    List<SettlementLine> findSales(@Param("sellerId") long sellerId, @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to);

    @Select("SELECT r.id, r.amount FROM refund r JOIN order_item oi ON oi.id = r.order_item_id "
            + "WHERE oi.seller_id = #{sellerId} AND r.status = 'COMPLETED' "
            + "AND r.refunded_at >= #{from} AND r.refunded_at < #{to} ORDER BY r.id")
    List<SettlementLine> findRefunds(@Param("sellerId") long sellerId, @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to);

    @Select("SELECT fee_rate FROM seller WHERE id = #{sellerId}")
    BigDecimal findFeeRate(@Param("sellerId") long sellerId);

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
}
