package in.schoolapp.audit.dto;

import in.schoolapp.audit.entity.AuditLog;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    String entityType,
    UUID entityId,
    String action,
    Map<String, Object> oldValues,
    Map<String, Object> newValues,
    UUID changedById,
    String changedByRole,
    String ipAddress,
    OffsetDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
            a.getId(),
            a.getEntityType(),
            a.getEntityId(),
            a.getAction() == null ? null : a.getAction().name(),
            a.getOldValues(),
            a.getNewValues(),
            a.getChangedById(),
            a.getChangedByRole(),
            a.getIpAddress(),
            a.getCreatedAt()
        );
    }
}
