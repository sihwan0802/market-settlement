package com.sihwan.market.product;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper {

    String COLUMNS = "p.id, p.seller_id, p.name, p.price, p.status, p.flash_sale, i.stock";

    @Select("SELECT id FROM seller WHERE member_id = #{memberId}")
    Optional<Long> findSellerId(@Param("memberId") long memberId);

    @Select("INSERT INTO product (seller_id, name, price, flash_sale) "
            + "VALUES (#{sellerId}, #{name}, #{price}, #{flashSale}) RETURNING id")
    long insertProduct(@Param("sellerId") long sellerId, @Param("name") String name,
                       @Param("price") long price, @Param("flashSale") boolean flashSale);

    @Update("INSERT INTO inventory (product_id, stock) VALUES (#{productId}, #{stock})")
    void insertInventory(@Param("productId") long productId, @Param("stock") int stock);

    @Select("SELECT " + COLUMNS + " FROM product p JOIN inventory i ON i.product_id = p.id WHERE p.id = #{id}")
    Optional<Product> findById(@Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM product p JOIN inventory i ON i.product_id = p.id "
            + "WHERE p.status <> 'HIDDEN' ORDER BY p.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Product> findVisible(@Param("size") int size, @Param("offset") int offset);

    @Update("UPDATE product SET name = #{name}, price = #{price}, status = #{status} WHERE id = #{id}")
    void updateProduct(@Param("id") long id, @Param("name") String name,
                       @Param("price") long price, @Param("status") String status);

    @Update("UPDATE inventory SET stock = #{stock}, updated_at = now() WHERE product_id = #{productId}")
    void updateStock(@Param("productId") long productId, @Param("stock") int stock);

    @Update("UPDATE product SET status = 'HIDDEN' WHERE id = #{id}")
    void hide(@Param("id") long id);
}
