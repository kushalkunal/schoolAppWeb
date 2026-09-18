package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.ExamType;
import in.schoolapp.academics.entity.FeePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateExamRequest(
    @NotBlank @Size(max = 100) String name,
    ExamType examType,
    LocalDate startDate,
    LocalDate endDate,
    /** Optional — scope this exam to a specific class (legacy single-class). */
    UUID classId,
    /** Optional — scope this exam to a specific section within the class. */
    UUID sectionId,
    /** Preferred — the classes participating in this exam. Students of these classes are auto-enrolled. */
    List<UUID> classIds,
    /** No-dues policy for admit cards. Defaults to BLOCK when omitted. */
    FeePolicy feePolicy,
    /** Academic session this exam belongs to. Falls back to the current session when omitted. */
    UUID academicYearId
) {}
