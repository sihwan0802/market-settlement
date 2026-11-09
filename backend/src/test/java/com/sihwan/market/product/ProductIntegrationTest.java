package com.sihwan.market.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class ProductIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

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

    private long createProduct(String token, String name, int stock) throws Exception {
        String res = mvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":12000,\"stock\":%d,\"flashSale\":false}".formatted(name, stock)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private static String updateBody(String name, int stock, String status) {
        return "{\"name\":\"%s\",\"price\":15000,\"stock\":%d,\"status\":\"%s\"}".formatted(name, stock, status);
    }

    @Test
    void seller_creates_and_anyone_reads() throws Exception {
        String seller = signupAndLogin("SELLER");
        long id = createProduct(seller, "수제 쿠키", 30);

        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수제 쿠키"))
                .andExpect(jsonPath("$.stock").value(30))
                .andExpect(jsonPath("$.status").value("ON_SALE"));
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void buyer_and_anonymous_cannot_create() throws Exception {
        String body = "{\"name\":\"x\",\"price\":1000,\"stock\":1,\"flashSale\":false}";
        String buyer = signupAndLogin("BUYER");
        mvc.perform(post("/api/products").header("Authorization", buyer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_updates_price_and_stock() throws Exception {
        String seller = signupAndLogin("SELLER");
        long id = createProduct(seller, "머그컵", 5);

        mvc.perform(put("/api/products/" + id).header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("머그컵 2", 8, "ON_SALE")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id))
                .andExpect(jsonPath("$.name").value("머그컵 2"))
                .andExpect(jsonPath("$.price").value(15000))
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    void other_seller_cannot_modify_or_delete() throws Exception {
        long id = createProduct(signupAndLogin("SELLER"), "남의 상품", 3);
        String other = signupAndLogin("SELLER");

        mvc.perform(put("/api/products/" + id).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("탈취", 99, "ON_SALE")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/" + id).header("Authorization", other))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.name").value("남의 상품"));
    }

    @Test
    void deleted_product_is_hidden_and_invalid_input_is_rejected() throws Exception {
        String seller = signupAndLogin("SELLER");
        long id = createProduct(seller, "삭제될 상품", 1);

        mvc.perform(delete("/api/products/" + id).header("Authorization", seller))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());

        mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"음수 재고\",\"price\":1000,\"stock\":-1,\"flashSale\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/products/999999999").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("없음", 1, "ON_SALE")))
                .andExpect(status().isNotFound());
    }
}
