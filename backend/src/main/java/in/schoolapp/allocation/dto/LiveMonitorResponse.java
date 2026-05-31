package in.schoolapp.allocation.dto;

import java.util.List;
import java.util.UUID;

/**
 * Real-time operational snapshot for the Live Teaching Monitor / Principal command center:
 * who is teaching now, who is free, what's coming next period, and who is on leave today.
 */
public record LiveMonitorResponse(
    PeriodInfo currentPeriod,   // null when no class is in session right now
    PeriodInfo nextPeriod,      // null when the teaching day is over
    List<OngoingClass> teachingNow,
    List<FreeTeacher> freeNow,
    List<UpcomingClass> upcoming,
    List<LeaveEntry> onLeave,
    Counts counts
) {
    public record PeriodInfo(String name, String startTime, String endTime) {}

    public record OngoingClass(UUID teacherId, String teacherName, String sectionLabel,
                               String subjectName, String periodName) {}

    public record FreeTeacher(UUID staffId, String name, String role) {}

    public record UpcomingClass(String teacherName, String sectionLabel, String subjectName, String time) {}

    public record LeaveEntry(UUID staffId, String name, String leaveType) {}

    public record Counts(int totalTeachers, int teachingNow, int freeNow, int onLeave) {}
}
