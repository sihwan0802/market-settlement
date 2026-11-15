package com.sihwan.market.payment;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PortOneClient {

    private final RestClient http;

    public PortOneClient(@Value("${app.portone.base-url}") String baseUrl,
                         @Value("${app.portone.api-secret}") String apiSecret) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
    }

    public PortOnePayment get(String paymentId) {
        try {
            return http.get().uri("/payments/{id}", paymentId).retrieve().body(PortOnePayment.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 서버 조회에 실패했습니다");
        }
    }

    public void cancel(String paymentId, long amount, String reason) {
        try {
            http.post().uri("/payments/{id}/cancel", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("reason", reason, "amount", amount))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 취소가 거절되었습니다");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 서버 취소 요청에 실패했습니다");
        }
    }
}
