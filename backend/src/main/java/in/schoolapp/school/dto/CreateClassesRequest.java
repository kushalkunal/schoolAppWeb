package in.schoolapp.school.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Onboarding step 2 payload. The principal declares the school's class structure in one shot:
 * a list of classes, each with its section names. Tiny schools may pass one class with one
 * section; bigger schools may declare 8 classes × 3 sections in the same request.
 */
public record CreateClassesRequest(
    @NotEmpty @Valid List<ClassSpec> classes
) {
    public record ClassSpec(
        @NotBlank @Size(max = 50) String name,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 10) String> sections,
        Integer sortOrder
    ) {}
}
