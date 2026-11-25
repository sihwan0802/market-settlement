package com.sihwan.market.settlement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SettlementNotifierTest {

    private HttpServer server(int status, AtomicReference<String> body, AtomicInteger calls) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext("/exec", ex -> {
            calls.incrementAndGet();
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) + "|" + ex.getRequestURI());
            ex.sendResponseHeaders(status, -1);
            ex.close();
        });
        s.start();
        return s;
    }

    @Test
    void posts_payload_json_to_the_configured_url() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer s = server(200, body, calls);
        try {
            new SettlementNotifier("http://localhost:" + s.getAddress().getPort() + "/exec?token=t")
                    .send(Map.of("type", "SETTLEMENT_DAILY", "created", 2, "missing", List.of()));
        } finally {
            s.stop(0);
        }
        assertEquals(1, calls.get());
        assertTrue(body.get().contains("\"type\":\"SETTLEMENT_DAILY\""));
        assertTrue(body.get().contains("\"created\":2"));
        assertTrue(body.get().endsWith("|/exec?token=t"));
    }

    @Test
    void blank_url_sends_nothing_and_failures_do_not_throw() throws Exception {
        assertDoesNotThrow(() -> new SettlementNotifier("").send(Map.of("a", 1)));

        AtomicInteger calls = new AtomicInteger();
        HttpServer s = server(500, new AtomicReference<>(), calls);
        try {
            assertDoesNotThrow(() -> new SettlementNotifier("http://localhost:" + s.getAddress().getPort() + "/exec")
                    .send(Map.of("a", 1)));
        } finally {
            s.stop(0);
        }
        assertEquals(1, calls.get());
        assertDoesNotThrow(() -> new SettlementNotifier("http://localhost:1/exec").send(Map.of("a", 1))); // 연결 거부
    }
}
