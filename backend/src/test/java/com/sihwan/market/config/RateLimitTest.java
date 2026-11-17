package com.sihwan.market.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.rate-limit.order-limit=3", "app.rate-limit.login-limit=2"})
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    // 다른 테스트가 세어 둔 횟수를 지운다
    @org.junit.jupiter.api.BeforeEach
    void clear() {
        redis.delete(redis.keys("rate:*"));
    }

    private MvcResult login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email))).andReturn();
    }

    @Test
    void login_is_limited_per_ip() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        assertEquals(401, login(email).getResponse().getStatus());
        assertEquals(401, login(email).getResponse().getStatus());
        MvcResult third = login(email);
        assertEquals(429, third.getResponse().getStatus());
        assertEquals("60", third.getResponse().getHeader("Retry-After"));
    }

    @Test
    void orders_are_limited_per_user_not_shared() throws Exception {
        String a = token();
        String b = token();
        for (int i = 0; i < 3; i++) {
            assertEquals(404, order(a));
        }
        assertEquals(429, order(a));
        assertEquals(404, order(b));
    }

    private int order(String token) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":999999999,\"quantity\":1}]}"))
                .andReturn().getResponse().getStatus();
    }

    // 로그인은 IP당 2회까지라 토큰 둘이면 딱 맞다
    private String token() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"BUYER\",\"storeName\":null}"
                        .formatted(email)));
        String res = login(email).getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }
}
