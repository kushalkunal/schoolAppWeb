package in.schoolapp.communication.dto;

import in.schoolapp.communication.entity.Circular;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record CircularResponse(
    UUID id,
    String title,
    String body,
    String targetType,
    List<UUID> targetIds,
    String language,
    String attachmentUrl,
    int sentCount,
    int deliveredCount,
    int readCount,
    int failedCount,
    OffsetDateTime sentAt,
    OffsetDateTime createdAt
) {
    public static CircularResponse from(Circular c) {
        return new CircularResponse(
            c.getId(),
            c.getTitle(),
            c.getBody(),
            c.getTargetType() == null ? null : c.getTargetType().name(),
            c.getTargetIds() == null ? List.of() : Arrays.asList(c.getTargetIds()),
            c.getLanguage(),
            c.getAttachmentUrl(),
            c.getSentCount(),
            c.getDeliveredCount(),
            c.getReadCount(),
            c.getFailedCount(),
            c.getSentAt(),
            c.getCreatedAt()
        );
    }
}
