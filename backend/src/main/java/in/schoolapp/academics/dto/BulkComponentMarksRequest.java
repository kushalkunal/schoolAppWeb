package in.schoolapp.academics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bulk component marks submission. One call covers all students × all components for a section.
 * <p>
 * The backend upserts each entry on {@code (configId, studentId)}, so re-submission is safe
 * (teacher corrections, offline sync). Setting {@code submitFinal=true} flips all written
 * entries to {@code is_draft=false} and triggers automatic result computation for the section.
 */
public record BulkComponentMarksRequest(
    @NotNull UUID sectionId,
    @NotEmpty @Valid List<ComponentMarkEntryDto> entries,
    boolean submitFinal
) {}
