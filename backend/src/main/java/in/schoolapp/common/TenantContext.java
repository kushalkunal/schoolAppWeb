package in.schoolapp.common;

import java.util.UUID;

/**
 * Per-request identity context, populated by the JWT filter from the access token and cleared
 * after the request completes. Every tenant-scoped DB query must filter by
 * {@link #getTenantId()} — this is the row-level multi-tenant boundary.
 * <p>
 * In this application a Tenant maps 1:1 to a School (the {@code school_id} column on every
 * tenant-scoped table). "Tenant" is used at the URL/auth layer for SaaS clarity; internal
 * services keep calling the value {@code schoolId} since that matches the DB column.
 * <p>
 * Stored in ThreadLocal so it flows through services/repositories without explicit passing.
 * Async tasks must re-propagate manually; ThreadLocal does not leak across executor threads.
 */
public final class TenantContext {

    private static final ThreadLocal<Identity> CTX = new ThreadLocal<>();

    private TenantContext() {}

    public record Identity(UUID tenantId, UUID staffId, String role) {}

    public static void set(UUID tenantId, UUID staffId, String role) {
        CTX.set(new Identity(tenantId, staffId, role));
    }

    public static UUID getTenantId() {
        Identity id = CTX.get();
        return id == null ? null : id.tenantId();
    }

    public static UUID getStaffId() {
        Identity id = CTX.get();
        return id == null ? null : id.staffId();
    }

    public static String getRole() {
        Identity id = CTX.get();
        return id == null ? null : id.role();
    }

    public static boolean isAuthenticated() {
        return CTX.get() != null;
    }

    public static void clear() {
        CTX.remove();
    }

    /**
     * Enforces that the current request's tenant matches the target tenant id. Guards against a
     * valid JWT being used to access another tenant's data via a path parameter. Typically
     * called by {@link TenantInterceptor} for every URI template that contains {@code tenantId}.
     */
    public static void validateTenant(UUID expectedTenantId) {
        UUID current = getTenantId();
        if (current == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        if (!current.equals(expectedTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN,
                "Request tenant does not match authenticated tenant");
        }
    }
}
