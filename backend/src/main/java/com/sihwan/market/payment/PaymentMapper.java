package com.sihwan.market.payment;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper {

    @Select("SELECT id, order_id, portone_payment_id, amount, status FROM payment "
            + "WHERE portone_payment_id = #{paymentId}")
    Optional<Payment> findByPortoneId(@Param("paymentId") String paymentId);

    @Update("INSERT INTO payment (order_id, portone_payment_id, idempotency_key, amount, status, paid_at) "
            + "VALUES (#{orderId}, #{paymentId}, #{paymentId}, #{amount}, 'PAID', now())")
    void insertPaid(@Param("orderId") long orderId, @Param("paymentId") String paymentId,
                    @Param("amount") long amount);

    @Select("SELECT id, order_id, portone_payment_id, amount, status FROM payment "
            + "WHERE order_id = #{orderId} AND status = 'PAID'")
    Optional<Payment> findPaidByOrderId(@Param("orderId") long orderId);

    @Update("UPDATE payment SET status = 'CANCELED' WHERE id = #{id}")
    void markCanceled(@Param("id") long id);
}
