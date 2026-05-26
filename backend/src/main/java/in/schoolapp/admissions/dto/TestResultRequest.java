package in.schoolapp.admissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TestResultRequest(
    @PositiveOrZero Integer totalMarks,
    @PositiveOrZero Integer obtainedMarks,
    @Size(max = 1000) String remarks,
    @Valid List<AdmissionTestScoreDto> perSubject
) {}
