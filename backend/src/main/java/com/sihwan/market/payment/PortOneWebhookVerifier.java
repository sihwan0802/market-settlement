package com.sihwan.market.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PortOne 웹훅 서명 검증 (Standard Webhooks). 서명 대상은 "{webhook-id}.{webhook-timestamp}.{원문 본문}"이고
 * HMAC-SHA256 키는 시크릿(앞의 whsec_ 제외)을 Base64로 푼 값이다. 시크릿이 없거나 잘못되면 모든 요청을 거부한다.
 */
@Component
public class PortOneWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(PortOneWebhookVerifier.class);
    private static final long TOLERANCE_SECONDS = 300; // 오래된 요청을 다시 보내는 공격을 막는다

    private final byte[] key;

    public PortOneWebhookVerifier(@Value("${app.portone.webhook-secret:}") String secret) {
        String base64 = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
        byte[] decoded = null;
        if (!base64.isBlank()) {
            try {
                decoded = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                log.error("PORTONE_WEBHOOK_SECRET이 Base64가 아니라서 웹훅을 모두 거부한다");
            }
        }
        this.key = decoded;
    }

    public boolean verify(String id, String timestamp, String signatureHeader, String body) {
        if (key == null || id == null || timestamp == null || signatureHeader == null) {
            return false;
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - sentAt) > TOLERANCE_SECONDS) {
            return false;
        }
        byte[] expected = hmac(id + "." + timestamp + "." + body);
        // 헤더는 "v1,서명 v1,서명" 처럼 공백으로 여러 개가 올 수 있다 (시크릿 교체 기간)
        for (String part : signatureHeader.split(" ")) {
            if (!part.startsWith("v1,")) {
                continue;
            }
            try {
                if (MessageDigest.isEqual(expected, Base64.getDecoder().decode(part.substring(3)))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 형식이 틀린 서명은 건너뛴다
            }
        }
        return false;
    }

    private byte[] hmac(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
