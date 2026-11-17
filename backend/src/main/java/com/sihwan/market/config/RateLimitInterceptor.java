package com.sihwan.market.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청 제한. 주문은 사용자별, 로그인은 IP별로 고정 구간 안에서 횟수를 센다.
 * Redis가 죽으면 제한 없이 통과시킨다(서비스가 멈추는 것보다 낫다고 봤다).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    // 첫 요청에서만 만료 시간을 건다
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int orderLimit;
    private final int orderWindow;
    private final int loginLimit;
    private final int loginWindow;

    public RateLimitInterceptor(StringRedisTemplate redis,
                                @Value("${app.rate-limit.order-limit}") int orderLimit,
                                @Value("${app.rate-limit.order-window-seconds}") int orderWindow,
                                @Value("${app.rate-limit.login-limit}") int loginLimit,
                                @Value("${app.rate-limit.login-window-seconds}") int loginWindow) {
        this.redis = redis;
        this.orderLimit = orderLimit;
        this.orderWindow = orderWindow;
        this.loginLimit = loginLimit;
        this.loginWindow = loginWindow;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String path = req.getRequestURI();
        boolean allowed = true;
        int retryAfter = 0;
        if (path.equals("/api/orders")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                allowed = within("order:" + auth.getName(), orderLimit, orderWindow);
                retryAfter = orderWindow;
            }
        } else if (path.equals("/api/auth/login")) {
            // ponytail: 프록시 뒤에서는 X-Forwarded-For를 써야 한다 (배포 때)
            allowed = within("login:" + req.getRemoteAddr(), loginLimit, loginWindow);
            retryAfter = loginWindow;
        }
        if (!allowed) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요\"}");
        }
        return allowed;
    }

    private boolean within(String subject, int limit, int windowSeconds) {
        try {
            Long n = redis.execute(COUNT, List.of("rate:" + subject), String.valueOf(windowSeconds));
            return n == null || n <= limit;
        } catch (RuntimeException e) {
            log.warn("요청 제한 확인 실패, 통과시킴: {}", e.toString());
            return true;
        }
    }
}
