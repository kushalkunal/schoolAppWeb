package in.schoolapp.academics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bulk create subjects at onboarding — idempotent (existing names skipped). */
public record CreateSubjectsRequest(
    @NotEmpty @Valid List<SubjectSpec> subjects
) {
    public record SubjectSpec(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 20) String code
    ) {}
}
