package com.sihwan.market.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class OrderQueryTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String signupAndLogin(String role) throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String store = "SELLER".equals(role) ? "\"가게\"" : "null";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"%s\",\"storeName\":%s}"
                        .formatted(email, role, store)));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }

    private long createProduct(String seller, String name) throws Exception {
        String res = mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":1000,\"stock\":50,\"flashSale\":false}".formatted(name)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private long order(String buyer, long productId, int qty) throws Exception {
        String res = mvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":%d,\"quantity\":%d}]}".formatted(productId, qty)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void buyer_sees_own_orders_newest_first_with_items() throws Exception {
        String seller = signupAndLogin("SELLER");
        long p = createProduct(seller, "청바지");
        String buyer = signupAndLogin("BUYER");
        String other = signupAndLogin("BUYER");
        long first = order(buyer, p, 1);
        long second = order(buyer, p, 2);

        mvc.perform(get("/api/orders").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].items[0].productName").value("청바지"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$[1].id").value(first));
        mvc.perform(get("/api/orders?size=1&page=1").header("Authorization", buyer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first));
        mvc.perform(get("/api/orders/" + first).header("Authorization", buyer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(1000));
        // 남의 주문은 없는 것처럼 404
        mvc.perform(get("/api/orders/" + first).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void seller_sees_only_paid_lines_of_own_products() throws Exception {
        String seller = signupAndLogin("SELLER");
        String otherSeller = signupAndLogin("SELLER");
        long mine = createProduct(seller, "내 상품");
        long theirs = createProduct(otherSeller, "남의 상품");
        String buyer = signupAndLogin("BUYER");
        long paid = order(buyer, mine, 3);
        order(buyer, mine, 1); // 결제 전(CREATED)이라 안 보인다
        order(buyer, theirs, 1);
        jdbc.update("UPDATE orders SET status = 'PAID' WHERE id = ?", paid);

        mvc.perform(get("/api/seller/orders").header("Authorization", seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(paid))
                .andExpect(jsonPath("$[0].productName").value("내 상품"))
                .andExpect(jsonPath("$[0].amount").value(3000));
    }

    @Test
    void roles_are_enforced() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        mvc.perform(get("/api/orders").header("Authorization", seller)).andExpect(status().isForbidden());
        mvc.perform(get("/api/seller/orders").header("Authorization", buyer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/seller/orders")).andExpect(status().isUnauthorized());
    }
}
