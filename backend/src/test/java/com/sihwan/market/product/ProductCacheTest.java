package com.sihwan.market.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
class ProductCacheTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    private String sellerToken() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"SELLER\",\"storeName\":\"가게\"}"
                        .formatted(email))).andExpect(status().isCreated());
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }

    private long createProduct(String seller, String name) throws Exception {
        String res = mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":1000,\"stock\":5,\"flashSale\":false}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void detail_is_served_from_cache_until_the_product_is_updated() throws Exception {
        String seller = sellerToken();
        long id = createProduct(seller, "원래 이름");

        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.name").value("원래 이름"));
        assertNotNull(redis.opsForValue().get("product:" + id));

        // DB를 직접 바꿔도 캐시가 살아 있는 동안은 옛 값이 나온다 (캐시를 거친다는 증거)
        jdbc.update("UPDATE product SET name = '직접 변경' WHERE id = ?", id);
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.name").value("원래 이름"));

        // API로 수정하면 캐시가 지워져 새 값이 나온다
        mvc.perform(put("/api/products/" + id).header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정 이름\",\"price\":2000,\"stock\":3,\"status\":\"ON_SALE\"}"))
                .andExpect(status().isOk());
        assertFalse(Boolean.TRUE.equals(redis.hasKey("product:" + id)));
        mvc.perform(get("/api/products/" + id))
                .andExpect(jsonPath("$.name").value("수정 이름"))
                .andExpect(jsonPath("$.price").value(2000));
    }

    @Test
    void hidden_product_is_not_served_from_cache() throws Exception {
        String seller = sellerToken();
        long id = createProduct(seller, "숨길 상품");
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/products/" + id)
                .header("Authorization", seller)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void list_cache_is_invalidated_when_a_product_is_created() throws Exception {
        String seller = sellerToken();
        createProduct(seller, "첫 상품");
        String before = mvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andReturn().getResponse().getContentAsString();

        long newId = createProduct(seller, "새 상품");
        String after = mvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(before.contains("\"id\":" + newId));
        assertTrue(after.contains("\"id\":" + newId));
        assertEquals(after, mvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andReturn().getResponse().getContentAsString());
    }
}
