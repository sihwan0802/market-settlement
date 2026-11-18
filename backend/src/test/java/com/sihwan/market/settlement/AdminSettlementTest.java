package com.sihwan.market.settlement;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class AdminSettlementTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private String login(String role) throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        if ("ADMIN".equals(role)) {
            jdbc.update("INSERT INTO member (email, password_hash, name, role) VALUES (?, ?, 'a', 'ADMIN')",
                    email, encoder.encode("password123"));
        } else {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"BUYER\"}".formatted(email)))
                    .andExpect(status().isCreated());
        }
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }

    @Test
    void only_admin_can_run_settlement_and_check_consistency() throws Exception {
        String buyer = login("BUYER");
        String admin = login("ADMIN");

        mvc.perform(post("/api/admin/settlements").param("date", "2019-01-01").header("Authorization", buyer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/settlements").param("date", "2019-01-01"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/settlements").param("date", "2019-01-01").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2019-01-01"))
                .andExpect(jsonPath("$.created").value(0));
        mvc.perform(get("/api/admin/settlements/inconsistent").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementIds").isArray());
        mvc.perform(get("/api/admin/settlements/missing").param("from", "2019-01-01").param("to", "2019-01-02")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/settlements/missing").param("from", "2019-01-01").param("to", "2019-01-02")
                        .header("Authorization", buyer))
                .andExpect(status().isForbidden());
    }
}
