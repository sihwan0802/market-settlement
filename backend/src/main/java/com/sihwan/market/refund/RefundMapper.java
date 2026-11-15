package com.sihwan.market.refund;

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
}
