package in.schoolapp.student.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request to bulk-promote all active students in a source section into a target section
 * belonging to the next academic year.
 *
 * <p>{@code failedStudentIds} lists the students who failed this year and should be retained
 * in the same class (not promoted). The service will enroll them in the matching same-class
 * section of the new academic year instead of the {@code targetSectionId}.
 *
 * <p>Students who have {@code LEFT} or {@code GRADUATED} status in the source section are
 * skipped silently.
 */
public record BulkPromotionRequest(
    @NotNull UUID sourceSectionId,
    @NotNull UUID targetSectionId,
    List<UUID> failedStudentIds,    // nullable / empty = no failures
    /** Optional: section for retained (failed) students. If null, retained students keep
     *  the same sectionId they came from (same class, new year). */
    UUID retainedSectionId
) {
    public List<UUID> failedStudentIds() {
        return failedStudentIds == null ? List.of() : failedStudentIds;
    }
}
