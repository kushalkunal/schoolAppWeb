package in.schoolapp.billing;

import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Blocks mutating HTTP requests when the calling tenant's subscription is in a non-allowed
 * state (SUSPENDED / CANCELLED). Reads run through unchanged so principals can still view
 * their data while sorting out billing.
 * <p>
 * Registered <strong>after</strong> {@code TenantInterceptor} in {@code WebMvcConfig} so
 * {@code TenantContext} is already populated when this runs.
 * <p>
 * Declared as a {@code @Bean} in {@link BillingConfig} (conditional on {@code SubscriptionService})
 * rather than {@code @Component}, so {@code @WebMvcTest} slices that don't include the billing
 * module don't fail to wire it.
 */
@Slf4j
@RequiredArgsConstructor
public class SubscriptionGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Paths that are always allowed regardless of subscription status — these must not be
     * blocked even when a tenant is suspended (so they can pay, log in, or be administered).
     */
    private static final Set<String> ALWAYS_ALLOWED_PREFIXES = Set.of(
        "/api/v1/auth/",
        "/api/v1/platform/",   // platform admin lifts suspensions
        "/webhooks/",
        "/api/v1/ping"
    );

    private final SubscriptionService subscriptionService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // Only enforce on writes.
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }

        // Skip allowlisted paths so the tenant has an escape hatch.
        String uri = request.getRequestURI();
        for (String prefix : ALWAYS_ALLOWED_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }

        // Anonymous — let SecurityConfig deny it.
        var tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return true;
        }

        SubscriptionStatus status = subscriptionService.effectiveStatus(tenantId);
        if (!status.allowsMutations()) {
            log.info("Blocked mutation tenant={} status={} uri={}", tenantId, status, uri);
            throw new AppException(ErrorCode.TENANT_SUSPENDED,
                "This school's subscription is " + status.name().toLowerCase()
                + ". Read access remains; mutating actions are disabled until reactivation.");
        }
        return true;
    }
}
