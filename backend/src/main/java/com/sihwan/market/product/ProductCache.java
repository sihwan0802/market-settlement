package com.sihwan.market.product;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** 상품 조회 캐시. Redis가 죽어도 조회는 DB로 계속된다. */
@Component
public class ProductCache {

    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);
    private static final String LIST_VERSION = "product:list:version";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public ProductCache(StringRedisTemplate redis, ObjectMapper json,
                        @Value("${app.cache.product-ttl-seconds}") long ttlSeconds) {
        this.redis = redis;
        this.json = json;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<Product> getDetail(long id) {
        try {
            String v = redis.opsForValue().get(detailKey(id));
            return v == null ? Optional.empty() : Optional.of(json.readValue(v, Product.class));
        } catch (RuntimeException e) {
            return failed("get detail", e);
        }
    }

    public void putDetail(Product p) {
        try {
            redis.opsForValue().set(detailKey(p.id()), json.writeValueAsString(p), ttl);
        } catch (RuntimeException e) {
            failed("put detail", e);
        }
    }

    public Optional<List<Product>> getList(int page, int size) {
        try {
            String v = redis.opsForValue().get(listKey(page, size));
            return v == null ? Optional.empty() : Optional.of(List.of(json.readValue(v, Product[].class)));
        } catch (RuntimeException e) {
            return failed("get list", e);
        }
    }

    public void putList(int page, int size, List<Product> list) {
        try {
            redis.opsForValue().set(listKey(page, size), json.writeValueAsString(list), ttl);
        } catch (RuntimeException e) {
            failed("put list", e);
        }
    }

    /** 커밋된 뒤에 지운다. 커밋 전에 지우면 그 사이 읽기가 옛 값을 다시 채울 수 있다. */
    public void evictAfterCommit(long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(id);
                }
            });
        } else {
            evict(id);
        }
    }

    // 목록 키에 버전을 넣어 두고 쓰기마다 버전을 올려, 목록 캐시 전체를 한 번에 무효화한다
    private void evict(long id) {
        try {
            redis.delete(detailKey(id));
            redis.opsForValue().increment(LIST_VERSION);
        } catch (RuntimeException e) {
            failed("evict", e);
        }
    }

    private String listKey(int page, int size) {
        String version = redis.opsForValue().get(LIST_VERSION);
        return "product:list:" + (version == null ? "0" : version) + ":" + page + ":" + size;
    }

    private static String detailKey(long id) {
        return "product:" + id;
    }

    private static <T> Optional<T> failed(String op, RuntimeException e) {
        log.warn("상품 캐시 {} 실패, DB로 처리: {}", op, e.toString());
        return Optional.empty();
    }
}
