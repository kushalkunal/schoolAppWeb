package in.schoolapp.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * Auto-enforces tenant boundary on every request: if the URI template contains a
 * {@code {tenantId}} variable, the interceptor compares it against the authenticated caller's
 * tenant (populated by {@code JwtAuthFilter}). Mismatch → 403, unauthenticated → 401.
 * <p>
 * This removes the need for controllers to call {@link TenantContext#validateTenant(UUID)}
 * explicitly — one interceptor keeps the rule impossible to forget.
 * <p>
 * Also clears {@link TenantContext} on completion as a safety net, even though
 * {@code JwtAuthFilter} already does try-finally cleanup. Defence in depth against thread-pool
 * leaks if a filter is ever bypassed.
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String PATH_VAR_TENANT_ID = "tenantId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVars = (Map<String, String>) request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVars == null || !pathVars.containsKey(PATH_VAR_TENANT_ID)) {
            return true;  // route has no tenant scoping
        }

        UUID pathTenant;
        try {
            pathTenant = UUID.fromString(pathVars.get(PATH_VAR_TENANT_ID));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Path variable tenantId is not a valid UUID");
        }

        TenantContext.validateTenant(pathTenant);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
