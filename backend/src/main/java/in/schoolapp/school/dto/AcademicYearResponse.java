package in.schoolapp.school.dto;

import in.schoolapp.school.entity.AcademicYear;

import java.time.LocalDate;
import java.util.UUID;

public record AcademicYearResponse(
    UUID id,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    boolean current
) {
    public static AcademicYearResponse from(AcademicYear y) {
        return new AcademicYearResponse(
            y.getId(),
            y.getName(),
            y.getStartDate(),
            y.getEndDate(),
            y.isCurrent()
        );
    }
}
