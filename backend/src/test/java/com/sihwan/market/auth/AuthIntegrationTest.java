package com.sihwan.market.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static String signupBody(String email, String role, String storeName) {
        String store = storeName == null ? "null" : "\"" + storeName + "\"";
        return """
                {"email":"%s","password":"password123","name":"tester","role":"%s","storeName":%s}
                """.formatted(email, role, store);
    }

    private void send(String url, String body, int expected) throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    private String login(String email, String password) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        return node.get("accessToken").asText();
    }

    @Test
    void buyer_signup_login_and_access_protected_api() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        send("/api/auth/signup", signupBody(email, "BUYER", null), 201);

        String token = login(email, "password123");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BUYER"));
    }

    @Test
    void protected_api_rejects_missing_token() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicate_email_is_conflict() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        send("/api/auth/signup", signupBody(email, "BUYER", null), 201);
        send("/api/auth/signup", signupBody(email, "BUYER", null), 409);
    }

    @Test
    void wrong_password_is_unauthorized() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        send("/api/auth/signup", signupBody(email, "BUYER", null), 201);
        send("/api/auth/login", "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email), 401);
    }

    @Test
    void seller_requires_store_name_and_admin_signup_is_rejected() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        send("/api/auth/signup", signupBody(email, "SELLER", null), 400);
        send("/api/auth/signup", signupBody(email, "ADMIN", null), 400);
        send("/api/auth/signup", signupBody(email, "SELLER", "내 가게"), 201);
    }
}
