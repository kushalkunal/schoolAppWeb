package in.schoolapp.migration.dto;

import in.schoolapp.migration.entity.MigrationJob;
import in.schoolapp.migration.entity.MigrationJobStatus;
import in.schoolapp.migration.entity.MigrationJobType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MigrationJobResponse(
    UUID id,
    MigrationJobType jobType,
    MigrationJobStatus status,
    String imageUrl,
    Integer recordCount,
    Integer matchedCount,
    String errorMessage,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt,
    List<ReviewableRecord> reviewableRecords    // populated only when status = REVIEW
) {
    public static MigrationJobResponse from(MigrationJob j, List<ReviewableRecord> records) {
        return new MigrationJobResponse(
            j.getId(),
            j.getJobType(),
            j.getStatus(),
            j.getImageUrl(),
            j.getRecordCount(),
            j.getMatchedCount(),
            j.getErrorMessage(),
            j.getCreatedAt(),
            j.getCompletedAt(),
            records
        );
    }

    public static MigrationJobResponse summary(MigrationJob j) {
        return from(j, null);
    }
}
