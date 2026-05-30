package in.schoolapp.academics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Configures the marking scheme for one subject in one exam.
 * <p>
 * Sending this request replaces all existing components for the
 * {@code (examId, subjectId)} pair atomically. Minimum one component required.
 * <p>
 * Example — Science subject with Theory + Practical:
 * <pre>
 * {
 *   "subjectId": "...",
 *   "components": [
 *     { "componentName": "Theory",    "maxMarks": 70, "passingMarks": 23, "sortOrder": 1 },
 *     { "componentName": "Practical", "maxMarks": 30, "passingMarks": 10, "sortOrder": 2 }
 *   ]
 * }
 * </pre>
 */
public record ConfigureExamStructureRequest(
    @NotNull UUID subjectId,
    @NotEmpty @Valid List<ComponentDefinition> components
) {
    public record ComponentDefinition(
        @NotBlank @Size(max = 50) String componentName,
        @NotNull @DecimalMin("0.5") BigDecimal maxMarks,
        BigDecimal passingMarks,
        int sortOrder
    ) {}
}
