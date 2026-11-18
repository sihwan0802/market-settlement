package com.sihwan.market.refund;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefundMapper {

    @Select("INSERT INTO refund (payment_id, order_item_id, amount, reason, status) "
            + "VALUES (#{paymentId}, #{orderItemId}, #{amount}, #{reason}, 'REQUESTED') RETURNING id")
    long insertRequested(@Param("paymentId") long paymentId, @Param("orderItemId") long orderItemId,
                         @Param("amount") long amount, @Param("reason") String reason);

    @Update("UPDATE refund SET status = 'COMPLETED', refunded_at = now() WHERE id = #{id} AND status = 'REQUESTED'")
    int markCompleted(@Param("id") long id);

    @Update("UPDATE refund SET status = 'FAILED' WHERE id = #{id} AND status = 'REQUESTED'")
    void markFailed(@Param("id") long id);

    @Select("SELECT count(*) FROM refund r JOIN payment p ON p.id = r.payment_id "
            + "WHERE p.order_id = #{orderId} AND r.status = 'COMPLETED'")
    int countCompleted(@Param("orderId") long orderId);

    record Stuck(long id, long paymentId, long orderItemId, long amount, long orderId, String portonePaymentId) {}

    @Select("SELECT r.id, r.payment_id, r.order_item_id, r.amount, p.order_id, p.portone_payment_id "
            + "FROM refund r JOIN payment p ON p.id = r.payment_id "
            + "WHERE r.status = 'REQUESTED' AND r.requested_at < now() - make_interval(mins => #{minutes}) "
            + "ORDER BY r.payment_id, r.id")
    List<Stuck> findStuck(@Param("minutes") int minutes);

    @Select("SELECT COALESCE(sum(amount), 0) FROM refund WHERE payment_id = #{paymentId} AND status = 'COMPLETED'")
    long sumCompleted(@Param("paymentId") long paymentId);
}
