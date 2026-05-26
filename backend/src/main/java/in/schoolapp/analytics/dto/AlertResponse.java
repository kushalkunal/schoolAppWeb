package in.schoolapp.analytics.dto;

import in.schoolapp.analytics.entity.Alert;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertResponse(
    UUID id,
    String alertType,
    String severity,
    UUID studentId,
    UUID sectionId,
    String title,
    String description,
    String actionUrl,
    boolean dismissed,
    OffsetDateTime dismissedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt
) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(
            a.getId(),
            a.getAlertType() == null ? null : a.getAlertType().name(),
            a.getSeverity() == null ? null : a.getSeverity().name(),
            a.getStudentId(),
            a.getSectionId(),
            a.getTitle(),
            a.getDescription(),
            a.getActionUrl(),
            a.isDismissed(),
            a.getDismissedAt(),
            a.getExpiresAt(),
            a.getCreatedAt()
        );
    }
}
