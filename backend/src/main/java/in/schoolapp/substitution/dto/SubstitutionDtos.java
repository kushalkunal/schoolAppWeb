package in.schoolapp.substitution.dto;

import java.util.List;
import java.util.UUID;

/** DTOs for the smart substitute-replacement planner. */
public final class SubstitutionDtos {
    private SubstitutionDtos() {}

    /** A teacher who is absent today, with the reason (attendance vs leave). */
    public record AbsentTeacher(UUID staffId, String name, String reason) {}

    /** A candidate substitute for one affected period, with ranking signals. */
    public record Candidate(
        UUID staffId,
        String name,
        String role,
        boolean sameSubject,     // teaches the same subject → best fit
        boolean teachesClass,    // already teaches this section → knows the students
        int periodsPerWeek,      // current workload (lower = preferred)
        boolean recommended      // the auto-pick for this period
    ) {}

    /** One period the absent teacher would have taught, with ranked substitute candidates. */
    public record PeriodPlan(
        UUID periodId,
        String periodName,
        String startTime,
        String endTime,
        UUID sectionId,
        String sectionLabel,
        UUID subjectId,
        String subjectName,
        UUID recommendedStaffId,    // null when nobody is free
        boolean alreadyCovered,     // a substitution already exists for this slot
        List<Candidate> candidates
    ) {}

    /** Full replacement plan for one absent teacher on one date. */
    public record ReplacementPlan(
        UUID absentTeacherId,
        String absentTeacherName,
        boolean isClassTeacher,
        List<String> classTeacherOf,
        List<PeriodPlan> periods
    ) {}

    /** Outcome of one-click auto-assign. */
    public record AutoAssignResult(int assigned, int skipped, List<String> messages) {}

    /** Principal dashboard counters for the day. */
    public record SubstitutionDashboard(
        int absentTeachers,
        int substitutionsAssigned,
        int pendingSubstitutions,
        int classesWithoutTeacher
    ) {}
}
