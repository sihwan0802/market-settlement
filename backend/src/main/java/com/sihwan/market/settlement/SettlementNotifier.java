package com.sihwan.market.settlement;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 정산 결과를 외부 자동화(Apps Script 웹 앱)로 보낸다. URL이 비어 있으면 아무것도 하지 않고,
 * 전송이 실패해도 정산에는 영향이 없다(로그만 남긴다).
 */
@Component
public class SettlementNotifier {

    private static final Logger log = LoggerFactory.getLogger(SettlementNotifier.class);

    private final String url;
    private final RestClient http;

    public SettlementNotifier(@Value("${app.notify.webhook-url:}") String url) {
        this.url = url;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public void send(Map<String, Object> payload) {
        if (url.isBlank()) {
            return;
        }
        try {
            http.post().uri(java.net.URI.create(url)).contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("정산 알림 전송 실패: {}", e.getMessage());
        }
    }
}
