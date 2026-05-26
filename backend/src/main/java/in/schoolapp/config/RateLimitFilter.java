package in.schoolapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple token-bucket rate limiter, keyed on the authenticated staff id (falling back to IP
 * for pre-auth endpoints). In-memory + single-instance — adequate for the Phase-1 deployment
 * topology (one Spring Boot node). A Redis-backed variant is an easy swap: replace {@link
 * #buckets} with a Redis INCR + TTL.
 * <p>
 * Applied to the whole {@code /api/v1/**} surface except auth endpoints (which have their own
 * OTP-request throttling inside {@code OtpService}). Runs after the JWT filter so the staff id
 * is already populated in {@link TenantContext}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** Per-key capacity refilled every window. 600 req/min is conservative for normal use
     *  (10 req/sec) but lets the frontend fan out on dashboard load without tripping. */
    @Value("${app.rate-limit.capacity:600}")
    private int capacity;

    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper;

    /** keyed on "staff:<uuid>" or "ip:<addr>". */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!enabled || shouldSkip(request)) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        long now = System.currentTimeMillis() / 1000;
        if (!bucket.tryConsume(now, capacity, windowSeconds)) {
            log.info("Rate limit exceeded key={} path={}", key, request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many requests; please retry later.")));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri == null
            || uri.startsWith("/actuator/")
            || uri.startsWith("/webhooks/")       // BSPs hit these hard by design
            || uri.startsWith("/api/v1/auth/")    // OTP has its own throttle
            || uri.equals("/api/v1/ping");
    }

    private static String resolveKey(HttpServletRequest req) {
        UUID staffId = TenantContext.getStaffId();
        if (staffId != null) return "staff:" + staffId;
        String xff = req.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank()
            ? (xff.indexOf(',') < 0 ? xff.trim() : xff.substring(0, xff.indexOf(',')).trim())
            : req.getRemoteAddr();
        return "ip:" + ip;
    }

    /** Token bucket with coarse second-resolution refill. */
    private static final class Bucket {
        private final AtomicLong tokens;
        private volatile long lastRefillSec;

        Bucket(int initial) {
            this.tokens = new AtomicLong(initial);
            this.lastRefillSec = System.currentTimeMillis() / 1000;
        }

        synchronized boolean tryConsume(long nowSec, int capacity, long windowSec) {
            long elapsed = nowSec - lastRefillSec;
            if (elapsed > 0) {
                long refill = (elapsed * capacity) / windowSec;
                if (refill > 0) {
                    long updated = Math.min(capacity, tokens.get() + refill);
                    tokens.set(updated);
                    lastRefillSec = nowSec;
                }
            }
            if (tokens.get() <= 0) return false;
            tokens.decrementAndGet();
            return true;
        }
    }
}
