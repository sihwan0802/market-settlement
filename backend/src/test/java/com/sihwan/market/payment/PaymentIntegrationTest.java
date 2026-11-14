package com.sihwan.market.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sihwan.market.order.OrderExpiryService;
import java.util.UUID;
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

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderExpiryService expiry;
    @MockitoBean PortOneClient portOne;

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

    private long order(String buyer, long productId, int qty) throws Exception {
        String res = mvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":%d,\"quantity\":%d}]}".formatted(productId, qty)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private int pay(String buyer, long orderId, String paymentId) throws Exception {
        return mvc.perform(post("/api/orders/%d/payments".formatted(orderId)).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\":\"%s\"}".formatted(paymentId)))
                .andReturn().getResponse().getStatus();
    }

    private void portOneReturns(String paymentId, String status, long total, String currency) {
        when(portOne.get(paymentId)).thenReturn(new PortOnePayment(status, new PortOnePayment.Amount(total), currency));
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int paymentCount(long orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment WHERE order_id = ?", Integer.class, orderId);
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    @Test
    void verified_payment_marks_order_paid() throws Exception {
        String buyer = signupAndLogin("BUYER");
        long product = createProduct(signupAndLogin("SELLER"), 5000, 10);
        long orderId = order(buyer, product, 2);
        String paymentId = "order-" + orderId + "-a1";
        portOneReturns(paymentId, "PAID", 10000, "KRW");

        mvc.perform(post("/api/orders/%d/payments".formatted(orderId)).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\":\"%s\"}".formatted(paymentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertEquals("PAID", orderStatus(orderId));
        assertEquals(1, paymentCount(orderId));
    }

    @Test
    void same_payment_id_twice_is_processed_once() throws Exception {
        String buyer = signupAndLogin("BUYER");
        long product = createProduct(signupAndLogin("SELLER"), 3000, 10);
        long orderId = order(buyer, product, 1);
        String paymentId = "order-" + orderId + "-dup";
        portOneReturns(paymentId, "PAID", 3000, "KRW");

        assertEquals(200, pay(buyer, orderId, paymentId));
        assertEquals(200, pay(buyer, orderId, paymentId));
        assertEquals(1, paymentCount(orderId));
    }

    @Test
    void wrong_amount_or_unpaid_status_is_rejected() throws Exception {
        String buyer = signupAndLogin("BUYER");
        long product = createProduct(signupAndLogin("SELLER"), 4000, 10);
        long orderId = order(buyer, product, 1);

        portOneReturns("order-" + orderId + "-low", "PAID", 100, "KRW");
        assertEquals(400, pay(buyer, orderId, "order-" + orderId + "-low"));

        portOneReturns("order-" + orderId + "-usd", "PAID", 4000, "USD");
        assertEquals(400, pay(buyer, orderId, "order-" + orderId + "-usd"));

        portOneReturns("order-" + orderId + "-ready", "READY", 4000, "KRW");
        assertEquals(409, pay(buyer, orderId, "order-" + orderId + "-ready"));

        assertEquals("CREATED", orderStatus(orderId));
        assertEquals(0, paymentCount(orderId));
    }

    @Test
    void other_buyer_and_foreign_payment_id_are_rejected() throws Exception {
        String buyer = signupAndLogin("BUYER");
        long product = createProduct(signupAndLogin("SELLER"), 1000, 10);
        long orderId = order(buyer, product, 1);
        long otherOrderId = order(buyer, product, 1);
        portOneReturns("order-" + otherOrderId + "-x", "PAID", 1000, "KRW");

        assertEquals(404, pay(signupAndLogin("BUYER"), orderId, "order-" + orderId + "-x"));
        assertEquals(400, pay(buyer, orderId, "order-" + otherOrderId + "-x"));
        assertEquals("CREATED", orderStatus(orderId));
    }

    @Test
    void unpaid_order_expires_and_stock_returns_but_paid_order_stays() throws Exception {
        String buyer = signupAndLogin("BUYER");
        long product = createProduct(signupAndLogin("SELLER"), 2000, 10);
        long unpaid = order(buyer, product, 3);
        long paid = order(buyer, product, 2);
        portOneReturns("order-" + paid + "-ok", "PAID", 4000, "KRW");
        assertEquals(200, pay(buyer, paid, "order-" + paid + "-ok"));
        assertEquals(5, stock(product));

        jdbc.update("UPDATE orders SET ordered_at = now() - interval '2 hours' WHERE id IN (?, ?)", unpaid, paid);
        expiry.expire(30);

        assertEquals("CANCELED", orderStatus(unpaid));
        assertEquals("PAID", orderStatus(paid));
        assertEquals(8, stock(product));
        assertEquals(409, pay(buyer, unpaid, "order-" + unpaid + "-late"));
    }
}
