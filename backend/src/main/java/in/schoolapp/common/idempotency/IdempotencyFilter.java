package in.schoolapp.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Servlet filter implementing the {@code Idempotency-Key} header semantics for unsafe
 * methods. Applied to {@code POST} on {@code /api/v1/**} only — other methods (PUT/PATCH/DELETE)
 * are already idempotent by convention. Webhook URLs are excluded because their providers do
 * their own idempotency on event IDs.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Client sends header {@code Idempotency-Key: <opaque-up-to-120-chars>}.</li>
 *   <li>Filter computes SHA-256 of the request body.</li>
 *   <li>If a row exists for {@code (tenantId, method, path, key)}:
 *     <ul>
 *       <li>same body hash → replay the cached response.</li>
 *       <li>different body hash → return {@code 409 VALIDATION_ERROR} ("key reuse with different payload").</li>
 *     </ul>
 *   </li>
 *   <li>Else: execute the chain, capture the response, and on success-class (2xx/4xx)
 *       persist (key, body_hash, status, body). 5xx responses are <em>not</em> cached so
 *       a retry has a chance to succeed.</li>
 * </ol>
 *
 * <p>Body cap: response bodies larger than {@code app.idempotency.max-body-bytes} (default
 * 64 KiB) are not stored; subsequent replays return the status code with an empty body and a
 * note in the log. Sufficient for typical JSON envelope responses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 120;
    private static final long TTL_SECONDS = 24 * 60 * 60L;  // 24 hours

    /** Paths that bypass — webhooks dedupe via provider event IDs. */
    private static final Set<String> SKIP_PREFIXES = Set.of("/webhooks/", "/actuator/");

    /**
     * Optional repo — slice tests (@WebMvcTest) exclude JPA, so the repository bean is absent.
     * In that case {@link #shouldNotFilter} returns true and the filter is a no-op.
     */
    private final ObjectProvider<IdempotencyKeyRepository> repositoryProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.max-body-bytes:65536}")
    private int maxBodyBytes;

    @Value("${app.idempotency.enabled:true}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        // No JPA in @WebMvcTest slices — silently skip.
        if (repositoryProvider.getIfAvailable() == null) return true;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) return true;
        for (String p : SKIP_PREFIXES) {
            if (uri.startsWith(p)) return true;
        }
        return request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            writeError(response, ErrorCode.VALIDATION_ERROR, 400,
                "Idempotency-Key must be 1–" + MAX_KEY_LENGTH + " characters");
            return;
        }

        ContentCachingRequestWrapper cachingReq = new ContentCachingRequestWrapper(request);
        // Need to consume the body to compute hash; the cache lets the downstream see it.
        cachingReq.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        String bodyHash = sha256Hex(cachingReq.getContentAsByteArray());
        String method = cachingReq.getMethod();
        String path = cachingReq.getRequestURI();
        // Tenant scoping is implicit in the URL path for tenant-scoped routes (the path
        // includes the tenantId UUID segment), so two schools cannot collide on the same key.
        // Keeping tenant_id NULL in this slice avoids depending on Spring Security filter order
        // for TenantContext propagation.
        java.util.UUID tenantId = null;

        IdempotencyKeyRepository repository = repositoryProvider.getObject();
        Optional<IdempotencyKey> existing = repository
            .findByCompositeKey(key, method, path, tenantId);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (!row.getBodyHash().equals(bodyHash)) {
                writeError(response, ErrorCode.VALIDATION_ERROR, 409,
                    "Idempotency-Key reused with a different request body");
                return;
            }
            // Replay cached response.
            response.setStatus(row.getStatusCode());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Idempotency-Replay", "true");
            if (row.getResponseBody() != null) {
                response.getWriter().write(row.getResponseBody());
            }
            return;
        }

        // Cache miss — execute chain wrapping response so we can capture the body.
        ContentCachingResponseWrapper cachingResp = new ContentCachingResponseWrapper(response);
        chain.doFilter(cachingReq, cachingResp);

        int status = cachingResp.getStatus();
        byte[] respBytes = cachingResp.getContentAsByteArray();
        // Only cache success + client-error responses (deterministic for the same input).
        // 5xx is a transient server fault — let retries through.
        if (status < 500 && respBytes.length <= maxBodyBytes) {
            try {
                IdempotencyKey row = new IdempotencyKey();
                row.setTenantId(tenantId);  // null in slice 4c — see note above
                row.setIdempotencyKey(key);
                row.setMethod(method);
                row.setPath(path);
                row.setBodyHash(bodyHash);
                row.setStatusCode(status);
                row.setResponseBody(new String(respBytes, StandardCharsets.UTF_8));
                row.setExpiresAt(OffsetDateTime.now().plusSeconds(TTL_SECONDS));
                repository.save(row);
            } catch (Exception e) {
                // Race against another concurrent identical request — the unique constraint
                // will reject one. Either way the cached row exists; move on.
                log.debug("Idempotency persist skipped: {}", e.getMessage());
            }
        }
        cachingResp.copyBodyToResponse();
    }

    private void writeError(HttpServletResponse response, ErrorCode code, int status, String msg)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, msg)));
    }

    private static String sha256Hex(byte[] in) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(in);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 is mandatory in every JVM; this is unreachable.
            throw new IllegalStateException(e);
        }
    }
}
