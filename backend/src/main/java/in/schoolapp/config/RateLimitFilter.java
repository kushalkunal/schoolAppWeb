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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Fixed-window rate limiter, keyed on the authenticated staff id (falling back to IP for
 * pre-auth endpoints). The counter lives in <b>Redis</b> ({@code INCR} + {@code EXPIRE}), so the
 * limit is enforced <b>globally across every backend instance</b> — running N nodes behind a load
 * balancer no longer multiplies the effective limit by N (the failure mode of the old in-memory
 * {@code ConcurrentHashMap} bucket). Same pattern already used for OTP throttling in
 * {@code OtpService}.
 * <p>
 * Fail-open: if Redis is briefly unreachable we allow the request rather than locking everyone
 * out — availability beats strict enforcement during an infra blip.
 * <p>
 * Applied to the whole {@code /api/v1/**} surface except auth endpoints (which have their own
 * OTP-request throttling). Runs after the JWT filter so the staff id is already populated in
 * {@link TenantContext}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** Max requests per key per window. 600/min is conservative for normal use (10 req/sec) but
     *  lets the frontend fan out on dashboard load without tripping. */
    @Value("${app.rate-limit.capacity:600}")
    private int capacity;

    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!enabled || shouldSkip(request)) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        if (!allowRequest(key)) {
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

    /**
     * Atomically increments the per-key counter in Redis. On the first hit of a window we set the
     * TTL so the window auto-resets. Returns {@code true} when the request is within the cap, or on
     * any Redis error (fail-open).
     */
    private boolean allowRequest(String key) {
        String redisKey = "ratelimit:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) return true;            // unexpected null → don't block
            if (count == 1L) {
                redis.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            return count <= capacity;
        } catch (Exception e) {
            log.warn("Rate-limit Redis unavailable, allowing request (key={}): {}", key, e.getMessage());
            return true;
        }
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
}
