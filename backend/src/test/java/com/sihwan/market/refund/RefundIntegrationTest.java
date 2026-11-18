package com.sihwan.market.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sihwan.market.payment.PortOneClient;
import com.sihwan.market.payment.PortOnePayment;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class RefundIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PortOneClient portOne;
    @Autowired RefundRecoveryService recovery;

    @BeforeEach
    void resetMock() {
        reset(portOne);
    }

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

    private long paidOrder(String buyer, long productA, long productB) throws Exception {
        String res = mvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":%d,\"quantity\":2},{\"productId\":%d,\"quantity\":1}]}"
                                .formatted(productA, productB)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(res).get("id").asLong();
        long total = json.readTree(res).get("totalAmount").asLong();
        String paymentId = "order-" + orderId + "-pay";
        when(portOne.get(paymentId)).thenReturn(new PortOnePayment("PAID", new PortOnePayment.Amount(total), "KRW"));
        mvc.perform(post("/api/orders/%d/payments".formatted(orderId)).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\":\"%s\"}".formatted(paymentId)))
                .andExpect(status().isOk());
        return orderId;
    }

    private long itemId(long orderId, long productId) {
        return jdbc.queryForObject("SELECT id FROM order_item WHERE order_id = ? AND product_id = ?",
                Long.class, orderId, productId);
    }

    private int refund(String buyer, long orderId, long itemId) throws Exception {
        return mvc.perform(post("/api/orders/%d/refunds".formatted(orderId)).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":%d,\"reason\":\"단순 변심\"}".formatted(itemId)))
                .andReturn().getResponse().getStatus();
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    private int refundCount(long orderId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM refund r JOIN payment p ON p.id = r.payment_id "
                + "WHERE p.order_id = ? AND r.status = ?", Integer.class, orderId, status);
    }

    @Test
    void partial_then_full_refund_updates_order_stock_and_payment() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);
        long orderId = paidOrder(buyer, a, b);
        assertEquals(8, stock(a));
        assertEquals(9, stock(b));

        mvc.perform(post("/api/orders/%d/refunds".formatted(orderId)).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":%d}".formatted(itemId(orderId, a))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PARTIAL_REFUNDED"));
        assertEquals(10, stock(a));
        assertEquals(9, stock(b));
        verify(portOne).cancel("order-" + orderId + "-pay", 10000L, "구매자 환불 요청");

        mvc.perform(post("/api/orders/%d/refunds".formatted(orderId)).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":%d}".formatted(itemId(orderId, b))))
                .andExpect(jsonPath("$.orderStatus").value("REFUNDED"));
        assertEquals("REFUNDED", orderStatus(orderId));
        assertEquals(2, refundCount(orderId, "COMPLETED"));
        assertEquals("CANCELED", jdbc.queryForObject(
                "SELECT status FROM payment WHERE order_id = ?", String.class, orderId));
    }

    @Test
    void same_item_cannot_be_refunded_twice() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);
        long orderId = paidOrder(buyer, a, b);
        long item = itemId(orderId, a);

        assertEquals(200, refund(buyer, orderId, item));
        assertEquals(409, refund(buyer, orderId, item));
        verify(portOne, times(1)).cancel(anyString(), anyLong(), anyString());
        assertEquals(10, stock(a));
    }

    @Test
    void rejected_cancel_leaves_order_untouched_and_can_be_retried() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);
        long orderId = paidOrder(buyer, a, b);
        long item = itemId(orderId, a);

        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "x"))
                .when(portOne).cancel(anyString(), anyLong(), anyString());
        assertEquals(409, refund(buyer, orderId, item));
        assertEquals("PAID", orderStatus(orderId));
        assertEquals(8, stock(a));
        assertEquals(1, refundCount(orderId, "FAILED"));

        reset(portOne);
        assertEquals(200, refund(buyer, orderId, item));
        assertEquals("PARTIAL_REFUNDED", orderStatus(orderId));
        assertEquals(10, stock(a));
    }

    @Test
    void unpaid_order_other_buyer_and_foreign_item_are_rejected() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);

        String res = mvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(a)))
                .andReturn().getResponse().getContentAsString();
        long unpaid = json.readTree(res).get("id").asLong();
        assertEquals(409, refund(buyer, unpaid, itemId(unpaid, a)));

        long paid = paidOrder(buyer, a, b);
        assertEquals(404, refund(signupAndLogin("BUYER"), paid, itemId(paid, a)));
        assertEquals(404, refund(buyer, paid, itemId(unpaid, a)));
        assertEquals("PAID", orderStatus(paid));
    }

    // 취소 요청에 응답을 못 받은 상황(시간 초과)을 만든다. 환불이 REQUESTED로 남는다.
    private long stuckRefund(String buyer, long orderId, long itemId) throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "x"))
                .when(portOne).cancel(anyString(), anyLong(), anyString());
        assertEquals(502, refund(buyer, orderId, itemId));
        reset(portOne);
        long refundId = jdbc.queryForObject("SELECT r.id FROM refund r WHERE r.order_item_id = ?",
                Long.class, itemId);
        jdbc.update("UPDATE refund SET requested_at = now() - interval '1 hour' WHERE id = ?", refundId);
        return refundId;
    }

    private void portOneShows(long orderId, long total, long cancelled) {
        when(portOne.get("order-" + orderId + "-pay"))
                .thenReturn(new PortOnePayment("PARTIAL_CANCELLED", new PortOnePayment.Amount(total, cancelled), "KRW"));
    }

    private String refundStatus(long refundId) {
        return jdbc.queryForObject("SELECT status FROM refund WHERE id = ?", String.class, refundId);
    }

    @Test
    void timeout_keeps_requested_and_recovery_completes_it_once() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);
        long orderId = paidOrder(buyer, a, b);
        long refundId = stuckRefund(buyer, orderId, itemId(orderId, a));
        assertEquals("REQUESTED", refundStatus(refundId));
        assertEquals(8, stock(a));
        // 같은 항목을 다시 요청해도 막힌다 (이중 환불 방지)
        assertEquals(409, refund(buyer, orderId, itemId(orderId, a)));

        portOneShows(orderId, 13000, 10000);
        assertEquals(1, recovery.recover(10));

        assertEquals("COMPLETED", refundStatus(refundId));
        assertEquals("PARTIAL_REFUNDED", orderStatus(orderId));
        assertEquals(10, stock(a));
        // 한 번 더 돌려도 재고가 늘지 않는다
        assertEquals(0, recovery.recover(10));
        assertEquals(10, stock(a));
    }

    @Test
    void recovery_marks_failed_when_portone_did_not_cancel_and_item_can_be_retried() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);
        long orderId = paidOrder(buyer, a, b);
        long refundId = stuckRefund(buyer, orderId, itemId(orderId, a));

        portOneShows(orderId, 13000, 0);
        assertEquals(1, recovery.recover(10));

        assertEquals("FAILED", refundStatus(refundId));
        assertEquals("PAID", orderStatus(orderId));
        assertEquals(8, stock(a));
        assertEquals(200, refund(buyer, orderId, itemId(orderId, a)));
        assertEquals(10, stock(a));
    }

    @Test
    void recovery_skips_recent_requests_and_handles_several_refunds_of_one_payment() throws Exception {
        String seller = signupAndLogin("SELLER");
        String buyer = signupAndLogin("BUYER");
        long a = createProduct(seller, 5000, 10);
        long b = createProduct(seller, 3000, 10);
        long orderId = paidOrder(buyer, a, b);
        long first = stuckRefund(buyer, orderId, itemId(orderId, a));
        long second = stuckRefund(buyer, orderId, itemId(orderId, b));

        // 방금 요청한 환불은 건드리지 않는다
        jdbc.update("UPDATE refund SET requested_at = now() WHERE id = ?", second);
        portOneShows(orderId, 13000, 10000);
        assertEquals(1, recovery.recover(10));
        assertEquals("COMPLETED", refundStatus(first));
        assertEquals("REQUESTED", refundStatus(second));

        // 둘 다 오래됐고 PortOne에는 첫 번째 금액만 취소돼 있으면 두 번째는 실패 처리
        jdbc.update("UPDATE refund SET requested_at = now() - interval '1 hour' WHERE id = ?", second);
        assertEquals(1, recovery.recover(10));
        assertEquals("FAILED", refundStatus(second));
        assertEquals(10, stock(a));
        assertEquals(9, stock(b));
    }
}
