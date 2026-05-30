package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.ExamType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateExamRequest(
    @NotBlank @Size(max = 100) String name,
    ExamType examType,
    LocalDate startDate,
    LocalDate endDate,
    /** Optional — scope this exam to a specific class. */
    UUID classId,
    /** Optional — scope this exam to a specific section within the class. */
    UUID sectionId
) {}
