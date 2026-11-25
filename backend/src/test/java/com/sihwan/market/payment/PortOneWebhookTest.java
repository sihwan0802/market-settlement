package com.sihwan.market.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.portone.webhook-secret=" + PortOneWebhookTest.SECRET)
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class PortOneWebhookTest {

    static final String SECRET = "whsec_" + "dGVzdC13ZWJob29rLXNlY3JldC1rZXk="; // Base64("test-webhook-secret-key")

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PortOneClient portOne;

    @BeforeEach
    void resetMock() {
        reset(portOne);
    }

    private String sign(String id, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring(6)), "HmacSHA256"));
        byte[] sig = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(sig);
    }

    private int webhook(String body, String signature, String timestamp) throws Exception {
        return mvc.perform(post("/api/webhooks/portone").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("webhook-id", "msg_1").header("webhook-timestamp", timestamp)
                        .header("webhook-signature", signature))
                .andReturn().getResponse().getStatus();
    }

    private String signedWebhook(String body) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        return mvc.perform(post("/api/webhooks/portone").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("webhook-id", "msg_1").header("webhook-timestamp", ts)
                        .header("webhook-signature", sign("msg_1", ts, body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String paid(String paymentId) {
        return "{\"type\":\"Transaction.Paid\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{\"paymentId\":\"%s\"}}"
                .formatted(paymentId);
    }

    private long newOrder(long price) throws Exception {
        String seller = login("SELLER");
        String buyer = login("BUYER");
        String p = mvc.perform(post("/api/products").header("Authorization", seller).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p\",\"price\":%d,\"stock\":10,\"flashSale\":false}".formatted(price)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String o = mvc.perform(post("/api/orders").header("Authorization", buyer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(json.readTree(p).get("id").asLong())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(o).get("id").asLong();
    }

    private String login(String role) throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String store = "SELLER".equals(role) ? "\"가게\"" : "null";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"t\",\"role\":\"%s\",\"storeName\":%s}"
                        .formatted(email, role, store))).andExpect(status().isCreated());
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("accessToken").asString();
    }

    private void portOneReturns(String paymentId, String status, long total) {
        when(portOne.get(paymentId)).thenReturn(new PortOnePayment(status, new PortOnePayment.Amount(total), "KRW"));
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int paymentCount(long orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment WHERE order_id = ?", Integer.class, orderId);
    }

    @Test
    void signed_webhook_marks_order_paid_without_login_and_repeat_is_same() throws Exception {
        long orderId = newOrder(5000);
        String paymentId = "order-" + orderId + "-w1";
        portOneReturns(paymentId, "PAID", 5000);

        assertEquals("{\"result\":\"PAID\"}", signedWebhook(paid(paymentId)));
        assertEquals("PAID", orderStatus(orderId));

        assertEquals("{\"result\":\"PAID\"}", signedWebhook(paid(paymentId)));
        assertEquals(1, paymentCount(orderId));
    }

    @Test
    void wrong_amount_is_rejected_and_order_stays_created() throws Exception {
        long orderId = newOrder(5000);
        String paymentId = "order-" + orderId + "-low";
        portOneReturns(paymentId, "PAID", 100);
        String body = paid(paymentId);
        String ts = String.valueOf(Instant.now().getEpochSecond());

        assertEquals(400, webhook(body, sign("msg_1", ts, body), ts));
        assertEquals("CREATED", orderStatus(orderId));
        assertEquals(0, paymentCount(orderId));
    }

    @Test
    void bad_missing_or_stale_signature_is_rejected() throws Exception {
        long orderId = newOrder(5000);
        String paymentId = "order-" + orderId + "-sig";
        portOneReturns(paymentId, "PAID", 5000);
        String body = paid(paymentId);
        String now = String.valueOf(Instant.now().getEpochSecond());
        String old = String.valueOf(Instant.now().getEpochSecond() - 3600);

        assertEquals(401, webhook(body, "v1," + Base64.getEncoder().encodeToString("x".getBytes()), now));
        assertEquals(401, webhook(body + " ", sign("msg_1", now, body), now)); // 본문이 바뀜
        assertEquals(401, webhook(body, sign("msg_1", old, body), old)); // 오래된 요청
        assertEquals(401, mvc.perform(post("/api/webhooks/portone").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus());
        assertEquals("CREATED", orderStatus(orderId));
    }

    @Test
    void other_types_and_foreign_payment_ids_are_ignored_with_200() throws Exception {
        assertEquals("{\"result\":\"IGNORED\"}",
                signedWebhook("{\"type\":\"Transaction.Ready\",\"data\":{\"paymentId\":\"order-1-x\"}}"));
        assertEquals("{\"result\":\"IGNORED\"}", signedWebhook(paid("someone-elses-payment")));
        assertEquals("{\"result\":\"IGNORED\"}", signedWebhook(paid("order-999999999-x")));
    }

    @Test
    void unpaid_status_returns_conflict_result_with_200() throws Exception {
        long orderId = newOrder(5000);
        String paymentId = "order-" + orderId + "-ready";
        portOneReturns(paymentId, "READY", 5000);

        assertEquals("{\"result\":\"CONFLICT\"}", signedWebhook(paid(paymentId)));
        assertEquals("CREATED", orderStatus(orderId));
    }

    @Test
    void verifier_without_secret_rejects_everything() throws Exception {
        PortOneWebhookVerifier none = new PortOneWebhookVerifier("");
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertFalse(none.verify("id", ts, sign("id", ts, "{}"), "{}"));
        assertTrue(new PortOneWebhookVerifier(SECRET).verify("id", ts, sign("id", ts, "{}"), "{}"));
    }
}
