package com.sihwan.market.product;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 선착순 상품 재고를 Redis에서 먼저 선점한다. 재고가 모자란 요청은 DB에 닿지 않고 거절된다.
 * 원본은 DB 재고이고, DB의 조건부 UPDATE가 마지막 방어선이다. Redis 값이 틀려도 초과 판매는 생기지 않는다.
 */
@Component
public class StockReservation {

    private static final Logger log = LoggerFactory.getLogger(StockReservation.class);
    // 키가 없으면 -2, 모자라면 -1, 선점하면 1
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local s = redis.call('GET', KEYS[1])
            if not s then return -2 end
            if tonumber(s) < tonumber(ARGV[1]) then return -1 end
            redis.call('DECRBY', KEYS[1], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return redis.call('INCRBY', KEYS[1], ARGV[1]) end
            return 0
            """, Long.class);
    // 값이 어긋나도 이 시간 뒤에는 DB 값으로 다시 맞춰진다
    private static final Duration TTL = Duration.ofSeconds(60);

    public enum Result { RESERVED, SOLD_OUT, SKIPPED }

    private final StringRedisTemplate redis;

    public StockReservation(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 트랜잭션 안에서 부른다. 롤백되면 선점한 수량을 돌려놓는다. Redis 오류면 건너뛰고 DB로 처리한다. */
    public Result reserve(Product p, int qty) {
        if (!p.flashSale()) {
            return Result.SKIPPED;
        }
        try {
            String key = key(p.id());
            Long r = redis.execute(RESERVE, List.of(key), String.valueOf(qty));
            if (r != null && r == -2) {
                redis.opsForValue().setIfAbsent(key, String.valueOf(p.stock()), TTL);
                r = redis.execute(RESERVE, List.of(key), String.valueOf(qty));
            }
            if (r == null || r == -1) {
                return Result.SOLD_OUT;
            }
            releaseOnRollback(p.id(), qty);
            return Result.RESERVED;
        } catch (RuntimeException e) {
            log.warn("재고 선점 실패, DB로 처리: {}", e.toString());
            return Result.SKIPPED;
        }
    }

    /** 트랜잭션(DB 연결)을 열기 전에 하는 빠른 거절. 선점 키가 있고 모자랄 때만 true. */
    public boolean isSoldOut(long productId, int qty) {
        try {
            String v = redis.opsForValue().get(key(productId));
            return v != null && Long.parseLong(v) < qty;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 재고가 DB에서 늘어난 경우(주문 만료, 환불, 판매자 수정). 커밋 뒤에 지워 다음 주문 때 DB 값으로 다시 채운다. */
    public void invalidateAfterCommit(long productId) {
        Runnable delete = () -> {
            try {
                redis.delete(key(productId));
            } catch (RuntimeException e) {
                log.warn("재고 선점 키 삭제 실패: {}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private void releaseOnRollback(long productId, int qty) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    redis.execute(RELEASE, List.of(key(productId)), String.valueOf(qty));
                } catch (RuntimeException e) {
                    log.warn("재고 선점 반환 실패: {}", e.toString());
                }
            }
        });
    }

    private static String key(long productId) {
        return "stock:" + productId;
    }
}
