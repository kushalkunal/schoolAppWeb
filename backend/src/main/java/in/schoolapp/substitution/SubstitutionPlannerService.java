package in.schoolapp.substitution;

import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.entity.TeacherSubjectAssignment;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.repository.LeaveApplicationRepository;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.SectionResponse;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.substitution.dto.SubstitutionDtos.*;
import in.schoolapp.timetable.TimetableService;
import in.schoolapp.timetable.dto.SubstitutionDto;
import in.schoolapp.timetable.entity.TimetableEntry;
import in.schoolapp.timetable.entity.TimetablePeriod;
import in.schoolapp.timetable.entity.TimetableSubstitution;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetablePeriodRepository;
import in.schoolapp.timetable.repository.TimetableSubstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart substitute planner. Detects absent teachers (self-attendance + approved leave), extracts
 * their affected periods from the timetable, ranks free substitutes period-wise, and can
 * auto-assign the best pick for every affected period in one shot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubstitutionPlannerService {

    private static final Set<StaffRole> TEACHING_ROLES =
        EnumSet.of(StaffRole.CLASS_TEACHER, StaffRole.SUBJECT_TEACHER, StaffRole.PRINCIPAL);
    private static final Set<StaffAttendance.StaffAttendanceStatus> ABSENT_STATES =
        EnumSet.of(StaffAttendance.StaffAttendanceStatus.ABSENT, StaffAttendance.StaffAttendanceStatus.LEAVE);

    private final TimetableEntryRepository entryRepository;
    private final TimetablePeriodRepository periodRepository;
    private final TimetableSubstitutionRepository substitutionRepository;
    private final StaffRepository staffRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final LeaveApplicationRepository leaveRepository;
    private final TeacherSubjectAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicYearService academicYearService;
    private final ClassSectionService classSectionService;
    private final TimetableService timetableService;

    // ── Absent teachers today (attendance + approved leave) ──────────────────────
    @Transactional(readOnly = true)
    public List<AbsentTeacher> absentTeachersToday(UUID tenantId, LocalDate date) {
        Map<UUID, Staff> staff = teachingStaff(tenantId);
        Map<UUID, String> reasons = absentReasons(tenantId, date, staff.keySet());
        return reasons.entrySet().stream()
            .filter(e -> staff.containsKey(e.getKey()))
            .map(e -> new AbsentTeacher(e.getKey(), staff.get(e.getKey()).displayName(), e.getValue()))
            .sorted(Comparator.comparing(AbsentTeacher::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** staffId → human reason, merged from attendance (ABSENT/LEAVE) and approved leave covering the date. */
    private Map<UUID, String> absentReasons(UUID tenantId, LocalDate date, Set<UUID> teachingIds) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (StaffAttendance a : staffAttendanceRepository.findBySchoolIdAndAttendanceDate(tenantId, date)) {
            if (ABSENT_STATES.contains(a.getStatus()) && teachingIds.contains(a.getStaffId())) {
                out.put(a.getStaffId(), a.getStatus() == StaffAttendance.StaffAttendanceStatus.LEAVE
                    ? "On leave" : "Marked absent");
            }
        }
        for (LeaveApplication l : leaveRepository.findBySchoolIdAndStatusOrderByCreatedAtDesc(
                tenantId, LeaveApplication.LeaveStatus.APPROVED)) {
            if (!teachingIds.contains(l.getStaffId())) continue;
            if (!l.getStartDate().isAfter(date) && !l.getEndDate().isBefore(date)) {
                boolean halfDay = l.getDays() != null && l.getDays().compareTo(BigDecimal.ONE) < 0;
                out.put(l.getStaffId(),
                    "Approved " + l.getLeaveType().name().toLowerCase() + (halfDay ? " (half-day)" : "") + " leave");
            }
        }
        return out;
    }

    // ── Replacement plan for one absent teacher ──────────────────────────────────
    @Transactional(readOnly = true)
    public ReplacementPlan buildPlan(UUID tenantId, UUID teacherId, LocalDate date) {
        Staff absent = staffRepository.findByIdAndSchoolId(teacherId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Teacher not found"));
        int dow = date.getDayOfWeek().getValue();

        Map<UUID, Staff> staff = teachingStaff(tenantId);
        Map<UUID, TimetablePeriod> periods = periodRepository.findBySchoolIdOrderBySortOrderAsc(tenantId)
            .stream().collect(Collectors.toMap(TimetablePeriod::getId, p -> p));
        Map<UUID, Subject> subjects = subjectRepository.findBySchoolIdOrderByName(tenantId)
            .stream().collect(Collectors.toMap(Subject::getId, s -> s));

        // section labels + class-teacher-of
        Map<UUID, String> sectionLabel = new HashMap<>();
        List<String> classTeacherOf = new ArrayList<>();
        for (ClassResponse c : classSectionService.listClasses(tenantId)) {
            for (SectionResponse s : c.sections()) {
                sectionLabel.put(s.id(), c.name() + " - " + s.name());
                if (teacherId.equals(s.classTeacherId())) classTeacherOf.add(c.name() + " - " + s.name());
            }
        }

        List<TimetableEntry> allEntries = entryRepository.findBySchoolId(tenantId);
        Set<UUID> absentIds = absentReasons(tenantId, date, staff.keySet()).keySet();

        // workload per teacher (periods/week)
        Map<UUID, Integer> workload = new HashMap<>();
        allEntries.stream().filter(e -> e.getTeacherId() != null)
            .forEach(e -> workload.merge(e.getTeacherId(), 1, Integer::sum));
        // subjects each teacher is assigned to
        Map<UUID, Set<UUID>> teacherSubjects = new HashMap<>();
        UUID yearId = academicYearService.getCurrentOrThrow(tenantId).getId();
        for (TeacherSubjectAssignment a : assignmentRepository.findBySchoolIdAndAcademicYearId(tenantId, yearId)) {
            teacherSubjects.computeIfAbsent(a.getStaffId(), k -> new HashSet<>()).add(a.getSubjectId());
        }
        // sections each teacher teaches (from the timetable)
        Map<UUID, Set<UUID>> teacherSections = new HashMap<>();
        allEntries.stream().filter(e -> e.getTeacherId() != null)
            .forEach(e -> teacherSections.computeIfAbsent(e.getTeacherId(), k -> new HashSet<>()).add(e.getSectionId()));

        List<TimetableSubstitution> subsToday = substitutionRepository.findByDateAndSchoolId(date, tenantId);

        List<TimetableEntry> affected = allEntries.stream()
            .filter(e -> teacherId.equals(e.getTeacherId()) && e.getDayOfWeek() == dow)
            .sorted(Comparator.comparing(e -> {
                TimetablePeriod p = periods.get(e.getPeriodId());
                return p != null ? p.getSortOrder() : 0;
            }))
            .toList();

        List<PeriodPlan> periodPlans = new ArrayList<>();
        for (TimetableEntry e : affected) {
            UUID periodId = e.getPeriodId();
            TimetablePeriod period = periods.get(periodId);

            // who is busy at this slot (teaching another section, or already substituting)
            Set<UUID> busy = allEntries.stream()
                .filter(x -> periodId.equals(x.getPeriodId()) && x.getDayOfWeek() == dow && x.getTeacherId() != null)
                .map(TimetableEntry::getTeacherId).collect(Collectors.toCollection(HashSet::new));
            subsToday.stream().filter(s -> s.getPeriodId().equals(periodId))
                .forEach(s -> busy.add(s.getSubstituteTeacherId()));
            boolean alreadyCovered = subsToday.stream()
                .anyMatch(s -> s.getPeriodId().equals(periodId) && s.getSectionId().equals(e.getSectionId()));

            List<Candidate> candidates = staff.values().stream()
                .filter(s -> !s.getId().equals(teacherId) && !absentIds.contains(s.getId()) && !busy.contains(s.getId()))
                .map(s -> new Candidate(
                    s.getId(), s.displayName(), s.getRole().name(),
                    e.getSubjectId() != null && teacherSubjects.getOrDefault(s.getId(), Set.of()).contains(e.getSubjectId()),
                    teacherSections.getOrDefault(s.getId(), Set.of()).contains(e.getSectionId()),
                    workload.getOrDefault(s.getId(), 0),
                    false))
                .sorted(Comparator
                    .comparing(Candidate::sameSubject).reversed()
                    .thenComparing(Comparator.comparing(Candidate::teachesClass).reversed())
                    .thenComparingInt(Candidate::periodsPerWeek)
                    .thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

            UUID recommended = candidates.isEmpty() ? null : candidates.get(0).staffId();
            // mark the recommended one
            List<Candidate> ranked = candidates.stream()
                .map(c -> c.staffId().equals(recommended)
                    ? new Candidate(c.staffId(), c.name(), c.role(), c.sameSubject(), c.teachesClass(), c.periodsPerWeek(), true)
                    : c)
                .toList();

            periodPlans.add(new PeriodPlan(
                periodId,
                period != null ? period.getName() : "—",
                period != null && period.getStartTime() != null ? period.getStartTime().toString() : null,
                period != null && period.getEndTime() != null ? period.getEndTime().toString() : null,
                e.getSectionId(),
                sectionLabel.getOrDefault(e.getSectionId(), "—"),
                e.getSubjectId(),
                e.getSubjectId() != null && subjects.containsKey(e.getSubjectId())
                    ? subjects.get(e.getSubjectId()).getName() : "—",
                recommended, alreadyCovered, ranked));
        }

        return new ReplacementPlan(teacherId, absent.displayName(), !classTeacherOf.isEmpty(), classTeacherOf, periodPlans);
    }

    // ── One-click auto-assign (each assignment in its own tx via TimetableService) ──
    public AutoAssignResult autoAssign(UUID tenantId, UUID teacherId, LocalDate date) {
        ReplacementPlan plan = buildPlan(tenantId, teacherId, date);
        int assigned = 0, skipped = 0;
        List<String> messages = new ArrayList<>();
        for (PeriodPlan p : plan.periods()) {
            if (p.alreadyCovered()) { skipped++; messages.add(p.sectionLabel() + " " + p.periodName() + ": already covered"); continue; }
            if (p.recommendedStaffId() == null) { skipped++; messages.add(p.sectionLabel() + " " + p.periodName() + ": no free teacher"); continue; }
            try {
                timetableService.assignSubstitute(tenantId, new SubstitutionDto(
                    null, p.sectionId(), p.periodId(), date, teacherId, p.recommendedStaffId(),
                    "Auto-assigned: " + plan.absentTeacherName() + " absent"));
                assigned++;
            } catch (AppException ex) {
                skipped++;
                messages.add(p.sectionLabel() + " " + p.periodName() + ": " + ex.getMessage());
            }
        }
        log.info("Auto-assign substitutes: teacher={} date={} assigned={} skipped={}", teacherId, date, assigned, skipped);
        return new AutoAssignResult(assigned, skipped, messages);
    }

    // ── Principal dashboard counters ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public SubstitutionDashboard dashboard(UUID tenantId, LocalDate date) {
        Map<UUID, Staff> staff = teachingStaff(tenantId);
        Set<UUID> absentIds = absentReasons(tenantId, date, staff.keySet()).keySet();
        int dow = date.getDayOfWeek().getValue();

        List<TimetableSubstitution> subsToday = substitutionRepository.findByDateAndSchoolId(date, tenantId);
        // periods the absent teachers should have taught today
        List<TimetableEntry> affected = entryRepository.findBySchoolId(tenantId).stream()
            .filter(e -> e.getDayOfWeek() == dow && e.getTeacherId() != null && absentIds.contains(e.getTeacherId()))
            .toList();
        long covered = affected.stream()
            .filter(e -> subsToday.stream().anyMatch(s ->
                s.getPeriodId().equals(e.getPeriodId()) && s.getSectionId().equals(e.getSectionId())))
            .count();
        int pending = affected.size() - (int) covered;

        return new SubstitutionDashboard(absentIds.size(), subsToday.size(), Math.max(0, pending), Math.max(0, pending));
    }

    private Map<UUID, Staff> teachingStaff(UUID tenantId) {
        return staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(tenantId).stream()
            .filter(s -> TEACHING_ROLES.contains(s.getRole()))
            .collect(Collectors.toMap(Staff::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
    }
}
