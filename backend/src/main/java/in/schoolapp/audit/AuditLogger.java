package in.schoolapp.audit;

import in.schoolapp.audit.entity.AuditAction;
import in.schoolapp.audit.entity.AuditLog;
import in.schoolapp.audit.repository.AuditLogRepository;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Writes {@link AuditLog} rows for significant state changes. Call from services (not
 * controllers) so the caller already holds the old and new state — the logger just snapshots.
 * <p>
 * Each method runs in {@code REQUIRES_NEW} so an audit insert failure can't roll back the
 * caller's transaction. Exceptions are caught and logged: a missed audit row is a compliance
 * ding; a rolled-back payment is a financial incident.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreate(UUID tenantId, String entityType, UUID entityId, Map<String, Object> newValues) {
        write(tenantId, entityType, entityId, AuditAction.CREATE, null, newValues);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUpdate(UUID tenantId, String entityType, UUID entityId,
                          Map<String, Object> oldValues, Map<String, Object> newValues) {
        write(tenantId, entityType, entityId, AuditAction.UPDATE, oldValues, newValues);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDelete(UUID tenantId, String entityType, UUID entityId, Map<String, Object> oldValues) {
        write(tenantId, entityType, entityId, AuditAction.DELETE, oldValues, null);
    }

    /**
     * Domain action not tied to a simple CRUD diff — "exam published", "alert dismissed",
     * "fees invoiced in bulk". The {@code details} map becomes {@code new_values} so the UI
     * can render human-readable context without a separate column.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(UUID tenantId, String entityType, UUID entityId,
                          String actionDescription, Map<String, Object> details) {
        Map<String, Object> newValues = details == null
            ? Map.of("action", actionDescription)
            : mergedWith(details, "action", actionDescription);
        write(tenantId, entityType, entityId, AuditAction.ACTION, null, newValues);
    }

    private void write(UUID tenantId, String entityType, UUID entityId, AuditAction action,
                       Map<String, Object> oldValues, Map<String, Object> newValues) {
        try {
            AuditLog row = new AuditLog();
            row.setSchoolId(tenantId);
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setAction(action);
            row.setOldValues(oldValues);
            row.setNewValues(newValues);
            row.setChangedById(TenantContext.getStaffId());
            row.setChangedByRole(TenantContext.getRole());
            row.setIpAddress(currentIpOrNull());
            repository.save(row);
        } catch (Exception e) {
            // Never propagate — audit must not break business writes.
            log.error("audit_log write failed entityType={} entityId={} action={} — {}",
                entityType, entityId, action, e.getMessage());
        }
    }

    private static Map<String, Object> mergedWith(Map<String, Object> base, String key, Object value) {
        java.util.HashMap<String, Object> out = new java.util.HashMap<>(base);
        out.put(key, value);
        return out;
    }

    /**
     * Best-effort client IP from the active request. Returns null when called outside a web
     * thread (e.g. a scheduled cron) — acceptable, the row is still written.
     */
    private static String currentIpOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        var req = sra.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First hop is the real client; the rest are proxies.
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }
}
