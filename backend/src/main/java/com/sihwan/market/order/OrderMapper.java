package com.sihwan.market.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper {

    // 확인과 차감을 한 쿼리로 처리한다. 영향 행이 0이면 재고 부족
    @Update("UPDATE inventory SET stock = stock - #{qty}, updated_at = now() "
            + "WHERE product_id = #{productId} AND stock >= #{qty}")
    int decreaseStock(@Param("productId") long productId, @Param("qty") int qty);

    @Select("INSERT INTO orders (buyer_id, status, total_amount) VALUES (#{buyerId}, 'CREATED', #{total}) RETURNING id")
    long insertOrder(@Param("buyerId") long buyerId, @Param("total") long total);

    @Update("INSERT INTO order_item (order_id, product_id, seller_id, unit_price, quantity, amount) "
            + "VALUES (#{orderId}, #{productId}, #{sellerId}, #{unitPrice}, #{qty}, #{amount})")
    void insertItem(@Param("orderId") long orderId, @Param("productId") long productId,
                    @Param("sellerId") long sellerId, @Param("unitPrice") long unitPrice,
                    @Param("qty") int qty, @Param("amount") long amount);
}
