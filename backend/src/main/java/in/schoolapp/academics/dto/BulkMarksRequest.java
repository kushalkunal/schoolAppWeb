package in.schoolapp.academics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Grid-style submission per gap analysis §5.4: teacher sees all students as rows, subject as
 * column, and saves the whole grid in one call. Upsert per {@code (examId, studentId,
 * subjectId)} so re-submission overwrites cleanly (teacher corrections + mobile offline sync).
 * <p>
 * {@code submitFinal=true} sets {@code is_draft=false} on all written rows — typically called
 * via a separate "Submit Final Marks" button after the teacher has filled in all values.
 * The field is named {@code submitFinal} (not {@code finalize}) because records cannot have a
 * component named {@code finalize} — it clashes with {@link java.lang.Object#finalize()}.
 */
public record BulkMarksRequest(
    @NotNull UUID sectionId,
    @NotEmpty @Valid List<MarkEntryDto> entries,
    boolean submitFinal
) {}
