package com.sihwan.market.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

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

    private long createProduct(String seller, long price, int stock) throws Exception {
        String res = mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p\",\"price\":%d,\"stock\":%d,\"flashSale\":false}".formatted(price, stock)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    private static String body(long... idAndQty) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < idAndQty.length; i += 2) {
            items.add("{\"productId\":%d,\"quantity\":%d}".formatted(idAndQty[i], idAndQty[i + 1]));
        }
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private int order(String token, String body) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }

    @Test
    void order_decreases_stock_and_copies_price() throws Exception {
        String seller = signupAndLogin("SELLER");
        long a = createProduct(seller, 10000, 5);
        long b = createProduct(seller, 2500, 5);
        String buyer = signupAndLogin("BUYER");

        mvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content(body(a, 2, b, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(27500));

        assertEquals(3, stock(a));
        assertEquals(2, stock(b));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM order_item WHERE product_id IN (?, ?) AND unit_price IN (10000, 2500)",
                Integer.class, a, b));
    }

    @Test
    void insufficient_stock_rolls_back_everything() throws Exception {
        String seller = signupAndLogin("SELLER");
        long a = createProduct(seller, 1000, 5);
        long b = createProduct(seller, 1000, 1);
        String buyer = signupAndLogin("BUYER");

        assertEquals(409, order(buyer, body(a, 2, b, 2)));
        assertEquals(5, stock(a));
        assertEquals(1, stock(b));
    }

    @Test
    void hidden_product_duplicate_and_roles_are_rejected() throws Exception {
        String seller = signupAndLogin("SELLER");
        long a = createProduct(seller, 1000, 5);
        String buyer = signupAndLogin("BUYER");

        assertEquals(400, order(buyer, body(a, 1, a, 1)));
        assertEquals(400, order(buyer, body(a, 0)));
        assertEquals(404, order(buyer, body(999999999L, 1)));
        assertEquals(403, order(seller, body(a, 1)));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body(a, 1)))
                .andExpect(status().isUnauthorized());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/products/" + a).header("Authorization", seller));
        assertEquals(404, order(buyer, body(a, 1)));
        assertEquals(5, stock(a));
    }

    @Test
    void concurrent_orders_never_oversell() throws Exception {
        String seller = signupAndLogin("SELLER");
        long id = createProduct(seller, 1000, 10);
        String buyer = signupAndLogin("BUYER");

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> order(buyer, body(id, 1));
            results.add(pool.submit(task));
        }
        int ok = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) ok++;
        }
        pool.shutdown();

        assertEquals(10, ok);
        assertEquals(0, stock(id));
    }
}
