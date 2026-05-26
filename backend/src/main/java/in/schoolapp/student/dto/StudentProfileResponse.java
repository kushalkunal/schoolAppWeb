package in.schoolapp.student.dto;

import java.util.List;
import java.util.UUID;

/** Full student view: base info + current enrollment + parents + siblings. */
public record StudentProfileResponse(
    StudentResponse student,
    EnrollmentSummary currentEnrollment,
    List<ParentDto> parents,
    List<StudentResponse> siblings
) {
    public record EnrollmentSummary(
        UUID enrollmentId,
        UUID academicYearId,
        String academicYearName,
        UUID sectionId,
        String className,
        String sectionName,
        Integer rollNumber,
        String status
    ) {}
}
