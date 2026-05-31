package in.schoolapp.allocation.dto;

import java.util.List;
import java.util.UUID;

/**
 * One-shot payload powering the Teacher Allocation Command Center: the class→section→subject→
 * teacher matrix, per-teacher workload, school-wide gaps, and the live "who is teaching now"
 * snapshot — so the whole centralized dashboard renders from a single request.
 */
public record TeacherAllocationOverviewResponse(
    Summary summary,
    List<ClassAllocation> classes,
    List<TeacherWorkload> teachers,
    NowTeaching now
) {
    public record Summary(
        int totalClasses,
        int totalSections,
        int totalTeachers,
        int sectionsWithoutClassTeacher,
        int sectionsWithoutSubjects,
        int teachersWithoutLoad,
        int totalSubjectAssignments,
        int workingDays,
        int periodsPerDay
    ) {}

    /** A teacher reference. {@code null} where unassigned. */
    public record Person(UUID staffId, String name, String role) {}

    public record SubjectAllocation(UUID subjectId, String subjectName, Person teacher) {}

    public record SectionAllocation(
        UUID sectionId,
        String sectionName,
        Person classTeacher,            // null → flagged as a gap in the UI
        List<SubjectAllocation> subjects
    ) {}

    public record ClassAllocation(UUID classId, String className, List<SectionAllocation> sections) {}

    public record TeacherWorkload(
        UUID staffId,
        String name,
        String role,
        List<String> classTeacherOf,    // section labels, e.g. "Class 5 - A"
        int subjectCount,
        int sectionCount,
        int periodsPerWeek,
        int freePeriods,
        int weeklyCapacity,
        int utilizationPct
    ) {}

    public record OngoingClass(String sectionLabel, String subjectName, String teacherName) {}

    public record NowTeaching(
        boolean inSession,
        String currentPeriodName,
        String startTime,
        String endTime,
        List<OngoingClass> ongoing
    ) {}
}
