package com.sihwan.market.order;

import java.util.List;
import java.util.Optional;
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

    @Select("SELECT id, buyer_id, status, total_amount FROM orders WHERE id = #{id}")
    Optional<Order> findById(@Param("id") long id);

    @Update("UPDATE orders SET status = 'PAID' WHERE id = #{id} AND status = 'CREATED'")
    int markPaid(@Param("id") long id);

    @Select("SELECT id FROM orders WHERE status = 'CREATED' AND ordered_at < now() - make_interval(mins => #{minutes})")
    List<Long> findExpiredIds(@Param("minutes") int minutes);

    @Update("UPDATE orders SET status = 'CANCELED' WHERE id = #{id} AND status = 'CREATED'")
    int cancel(@Param("id") long id);

    @Update("UPDATE inventory SET stock = stock + oi.quantity, updated_at = now() "
            + "FROM order_item oi WHERE oi.order_id = #{orderId} AND inventory.product_id = oi.product_id")
    void restoreStock(@Param("orderId") long orderId);

    @Select("SELECT id, order_id, product_id, quantity, amount FROM order_item WHERE id = #{itemId} AND order_id = #{orderId}")
    Optional<OrderItem> findItem(@Param("orderId") long orderId, @Param("itemId") long itemId);

    @Select("SELECT product_id FROM order_item WHERE order_id = #{orderId}")
    List<Long> findProductIds(@Param("orderId") long orderId);

    @Select("SELECT count(*) FROM order_item WHERE order_id = #{orderId}")
    int countItems(@Param("orderId") long orderId);

    @Select("SELECT id FROM orders WHERE id = #{id} FOR UPDATE")
    long lockById(@Param("id") long id);

    @Update("UPDATE orders SET status = #{status} WHERE id = #{id} AND status IN ('PAID', 'PARTIAL_REFUNDED')")
    int updateRefundStatus(@Param("id") long id, @Param("status") String status);

    @Update("UPDATE inventory SET stock = stock + #{qty}, updated_at = now() WHERE product_id = #{productId}")
    void increaseStock(@Param("productId") long productId, @Param("qty") int qty);

    @Select("SELECT id, status, total_amount, ordered_at FROM orders WHERE buyer_id = #{buyerId} "
            + "ORDER BY ordered_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<OrderDtos.OrderRow> findByBuyer(@Param("buyerId") long buyerId, @Param("limit") int limit,
                                         @Param("offset") int offset);

    @Select("SELECT id, status, total_amount, ordered_at FROM orders WHERE id = #{id} AND buyer_id = #{buyerId}")
    Optional<OrderDtos.OrderRow> findOneByBuyer(@Param("id") long id, @Param("buyerId") long buyerId);

    @Select("<script>SELECT oi.id, oi.order_id, oi.product_id, p.name AS product_name, oi.unit_price, oi.quantity, oi.amount "
            + "FROM order_item oi JOIN product p ON p.id = oi.product_id "
            + "WHERE oi.order_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
            + "ORDER BY oi.id</script>")
    List<OrderDtos.ItemRow> findItemsByOrderIds(@Param("ids") List<Long> ids);

    // 결제되지 않은 주문(CREATED, CANCELED)은 판매가 아니므로 뺀다
    @Select("SELECT o.id AS order_id, oi.id AS item_id, o.status, o.ordered_at, oi.product_id, p.name AS product_name, "
            + "oi.unit_price, oi.quantity, oi.amount "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN product p ON p.id = oi.product_id "
            + "WHERE oi.seller_id = #{sellerId} AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') "
            + "ORDER BY oi.order_id DESC, oi.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<OrderDtos.SellerLine> findSellerLines(@Param("sellerId") long sellerId, @Param("limit") int limit,
                                               @Param("offset") int offset);
}
