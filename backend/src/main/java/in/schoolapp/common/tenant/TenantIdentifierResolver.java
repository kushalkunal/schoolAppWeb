package in.schoolapp.common.tenant;

import in.schoolapp.common.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import java.util.UUID;

/**
 * Tells Hibernate which tenant the current session belongs to, read from {@link TenantContext}
 * (populated per-request by {@code JwtAuthFilter}). The value is handed to
 * {@link RlsTenantConnectionProvider}, which binds it onto the JDBC connection as the
 * {@code app.current_tenant} GUC that the V24 row-level-security policies key on.
 * <p>
 * When there is no tenant on the thread — login/OTP/signup, webhooks (tenant resolved from the
 * payload), platform admin, and the cross-tenant schedulers — we resolve to {@link #SYSTEM}.
 * The provider leaves the GUC unset for SYSTEM, which makes the RLS policy permissive, so trusted
 * tenant-less work keeps seeing every tenant exactly as before.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    /** Sentinel for "no authenticated tenant on this thread" — must never collide with a UUID. */
    public static final String SYSTEM = "SYSTEM";

    @Override
    public String resolveCurrentTenantIdentifier() {
        UUID tenant = TenantContext.getTenantId();
        return tenant != null ? tenant.toString() : SYSTEM;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Sessions are request-scoped and never reused across tenants, so no validation needed.
        return false;
    }
}
