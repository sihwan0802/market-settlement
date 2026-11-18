package com.sihwan.market.settlement;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 같은 날짜의 정산이 동시에 두 번 돌지 않게 하는 Redis 락.
 * 정산은 중복 실행해도 결과가 같다(UNIQUE). 락은 같은 일을 두 번 하는 낭비를 막는 용도라
 * Redis에 문제가 있으면 락 없이 진행한다.
 */
@Component
public class SettlementLock {

    private static final Logger log = LoggerFactory.getLogger(SettlementLock.class);
    // 실행 도중 서버가 죽어도 이 시간 뒤에는 다시 실행할 수 있다
    private static final Duration TTL = Duration.ofMinutes(30);
    // 내가 건 락일 때만 지운다
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public SettlementLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public <T> T run(LocalDate date, Supplier<T> job) {
        String key = "lock:settlement:" + date;
        String token = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, TTL));
            if (!locked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, date + " 정산이 이미 실행 중입니다");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("정산 락 획득 실패, 락 없이 진행: {}", e.toString());
            return job.get();
        }
        try {
            return job.get();
        } finally {
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (RuntimeException e) {
                log.warn("정산 락 해제 실패(만료로 풀림): {}", e.toString());
            }
        }
    }
}
