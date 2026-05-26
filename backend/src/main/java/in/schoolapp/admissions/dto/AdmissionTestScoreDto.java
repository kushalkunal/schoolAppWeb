package in.schoolapp.admissions.dto;

import in.schoolapp.admissions.entity.AdmissionTestScore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdmissionTestScoreDto(
    @NotBlank @Size(max = 100) String subjectName,
    @Positive int maxMarks,
    @PositiveOrZero int obtainedMarks,
    @Size(max = 500) String remarks
) {
    public static AdmissionTestScoreDto from(AdmissionTestScore s) {
        return new AdmissionTestScoreDto(s.getSubjectName(), s.getMaxMarks(), s.getObtainedMarks(), s.getRemarks());
    }
}
