package com.sihwan.market.payment;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PortOne 웹훅. 서명을 검증한 뒤에도 본문의 금액이나 상태는 믿지 않고, 결제 번호만 받아 PortOne에 다시 조회해서 처리한다.
 * 처리했거나 우리와 상관없는 요청은 200, 서명 오류는 401, 금액 불일치 같은 거절은 4xx, 조회 실패는 5xx(PortOne이 다시 보낸다).
 */
@RestController
@RequestMapping("/api/webhooks/portone")
public class PortOneWebhookController {

    private final PortOneWebhookVerifier verifier;
    private final PaymentService payments;
    private final ObjectMapper json;

    public PortOneWebhookController(PortOneWebhookVerifier verifier, PaymentService payments, ObjectMapper json) {
        this.verifier = verifier;
        this.payments = payments;
        this.json = json;
    }

    @PostMapping
    public Map<String, String> receive(@RequestBody String body,
                                       @RequestHeader(value = "webhook-id", required = false) String id,
                                       @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
                                       @RequestHeader(value = "webhook-signature", required = false) String signature) {
        if (!verifier.verify(id, timestamp, signature, body)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다");
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "웹훅 본문을 읽을 수 없습니다");
        }
        if (!"Transaction.Paid".equals(root.path("type").asString(""))) {
            return Map.of("result", "IGNORED"); // 모르는 타입은 오류 없이 무시 (PortOne 안내)
        }
        String paymentId = root.path("data").path("paymentId").asString("");
        if (paymentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 번호가 없습니다");
        }
        return Map.of("result", payments.syncPaid(paymentId));
    }
}
