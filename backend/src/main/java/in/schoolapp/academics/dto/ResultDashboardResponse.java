package in.schoolapp.academics.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Analytics dashboard for one exam — toppers, pass rates, grade distribution. */
public record ResultDashboardResponse(
    UUID examId,
    String examName,
    List<SectionSummary> sections
) {
    public record SectionSummary(
        UUID sectionId,
        String sectionName,
        int totalStudents,
        long passCount,
        long failCount,
        BigDecimal passPercentage,
        TopperDto topper,
        List<SubjectAverage> subjectAverages,
        Map<String, Long> gradeDistribution
    ) {}

    public record TopperDto(
        UUID studentId,
        String name,
        BigDecimal percentage,
        String grade
    ) {}

    public record SubjectAverage(
        UUID subjectId,
        String subjectName,
        BigDecimal averageObtained,
        BigDecimal maxMarks
    ) {}
}
