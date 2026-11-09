package com.sihwan.market.common;

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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class ErrorResponseTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String signup(String email, String role) throws Exception {
        String store = "SELLER".equals(role) ? "\"가게\"" : "null";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"%s\",\"storeName\":%s}"
                        .formatted(email, role, store)));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }

    @Test
    void unauthorized_and_forbidden_use_common_format() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String buyer = signup(UUID.randomUUID() + "@test.com", "BUYER");
        mvc.perform(post("/api/products").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"price\":1000,\"stock\":1,\"flashSale\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void not_found_and_conflict_use_common_format() throws Exception {
        mvc.perform(get("/api/products/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("상품이 없습니다"));

        String email = UUID.randomUUID() + "@test.com";
        signup(email, "BUYER");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"BUYER\"}".formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void validation_error_lists_fields() throws Exception {
        String seller = signup(UUID.randomUUID() + "@test.com", "SELLER");
        mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":1000,\"stock\":-1,\"flashSale\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='stock')]").exists());

        mvc.perform(post("/api/products").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{깨진 json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
