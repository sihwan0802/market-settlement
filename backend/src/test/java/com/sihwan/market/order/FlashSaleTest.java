package com.sihwan.market.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class FlashSaleTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired OrderExpiryService expiry;

    private String signupAndLogin(String role) throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String store = "SELLER".equals(role) ? "\"가게\"" : "null";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"%s\",\"storeName\":%s}"
                                .formatted(email, role, store)))
                .andExpect(status().isCreated());
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }

    private long createProduct(String seller, int stock, boolean flash) throws Exception {
        String res = mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p\",\"price\":1000,\"stock\":%d,\"flashSale\":%b}".formatted(stock, flash)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private int order(String token, long productId, int qty) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":%d,\"quantity\":%d}]}".formatted(productId, qty)))
                .andReturn().getResponse().getStatus();
    }

    private int dbStock(long productId) {
        return jdbc.queryForObject("SELECT stock FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    @Test
    void flash_sale_never_oversells_and_redis_matches_db() throws Exception {
        String seller = signupAndLogin("SELLER");
        long id = createProduct(seller, 10, true);
        String buyer = signupAndLogin("BUYER");

        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            results.add(pool.submit(() -> order(buyer, id, 1)));
        }
        int ok = 0;
        int soldOut = 0;
        for (Future<Integer> f : results) {
            int s = f.get();
            if (s == 201) ok++;
            if (s == 409) soldOut++;
        }
        pool.shutdown();

        assertEquals(10, ok);
        assertEquals(90, soldOut);
        assertEquals(0, dbStock(id));
        assertEquals("0", redis.opsForValue().get("stock:" + id));
    }

    @Test
    void failed_order_gives_back_the_reservation() throws Exception {
        String seller = signupAndLogin("SELLER");
        long flash = createProduct(seller, 5, true);
        long plain = createProduct(seller, 0, false);
        String buyer = signupAndLogin("BUYER");

        // 선점은 성공하지만 뒤 항목 때문에 주문 전체가 롤백된다
        String body = "{\"items\":[{\"productId\":%d,\"quantity\":2},{\"productId\":%d,\"quantity\":1}]}"
                .formatted(flash, plain);
        int status = mvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();

        assertEquals(409, status);
        assertEquals(5, dbStock(flash));
        assertEquals("5", redis.opsForValue().get("stock:" + flash));
    }

    @Test
    void expired_order_refreshes_reservation_from_db() throws Exception {
        String seller = signupAndLogin("SELLER");
        long id = createProduct(seller, 3, true);
        String buyer = signupAndLogin("BUYER");

        assertEquals(201, order(buyer, id, 3));
        assertEquals("0", redis.opsForValue().get("stock:" + id));

        jdbc.update("UPDATE orders SET ordered_at = now() - interval '1 day' WHERE id IN "
                + "(SELECT order_id FROM order_item WHERE product_id = ?)", id);
        expiry.expire(30);

        assertEquals(3, dbStock(id));
        assertNull(redis.opsForValue().get("stock:" + id));
        assertEquals(201, order(buyer, id, 3));
    }
}
