package in.schoolapp.student.dto;

import java.util.List;
import java.util.UUID;

/** Summary returned after a bulk promotion run. */
public record BulkPromotionResponse(
    int promoted,
    int retained,
    int skipped,
    List<UUID> promotedStudentIds,
    List<UUID> retainedStudentIds,
    List<UUID> skippedStudentIds
) {}
