package in.schoolapp.allocation;

import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.entity.TeacherSubjectAssignment;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.allocation.dto.TeacherAllocationOverviewResponse;
import in.schoolapp.allocation.dto.TeacherAllocationOverviewResponse.*;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.SectionResponse;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.timetable.entity.TimetableEntry;
import in.schoolapp.timetable.entity.TimetablePeriod;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetablePeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates classes, sections, subject assignments, class-teacher assignments and the timetable
 * into a single allocation overview. All reads; computes workload + the live "now teaching"
 * snapshot in memory.
 */
@Service
@RequiredArgsConstructor
public class TeacherAllocationService {

    private static final Set<StaffRole> TEACHING_ROLES =
        EnumSet.of(StaffRole.CLASS_TEACHER, StaffRole.SUBJECT_TEACHER, StaffRole.PRINCIPAL);

    private final ClassSectionService classSectionService;
    private final AcademicYearService academicYearService;
    private final TeacherSubjectAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimetablePeriodRepository periodRepository;

    @Transactional(readOnly = true)
    public TeacherAllocationOverviewResponse getOverview(UUID tenantId) {
        UUID yearId = academicYearService.getCurrentOrThrow(tenantId).getId();

        List<ClassResponse> classes = classSectionService.listClasses(tenantId);
        List<TeacherSubjectAssignment> assignments =
            assignmentRepository.findBySchoolIdAndAcademicYearId(tenantId, yearId);
        Map<UUID, Subject> subjectsById = subjectRepository.findBySchoolIdOrderByName(tenantId).stream()
            .collect(Collectors.toMap(Subject::getId, s -> s));
        Map<UUID, Staff> staffById = staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(tenantId).stream()
            .collect(Collectors.toMap(Staff::getId, s -> s));
        List<TimetableEntry> entries = entryRepository.findBySchoolId(tenantId);
        List<TimetablePeriod> teachingPeriods = periodRepository.findBySchoolIdOrderBySortOrderAsc(tenantId)
            .stream().filter(p -> !p.isBreakSlot()).toList();

        // section label map (e.g. "Class 5 - A") for workload + now-teaching
        Map<UUID, String> sectionLabel = new LinkedHashMap<>();
        for (ClassResponse c : classes) {
            for (SectionResponse s : c.sections()) {
                sectionLabel.put(s.id(), c.name() + " - " + s.name());
            }
        }

        // assignments grouped by section
        Map<UUID, List<TeacherSubjectAssignment>> bySection = assignments.stream()
            .collect(Collectors.groupingBy(TeacherSubjectAssignment::getSectionId));

        // ---- Build the class → section → subject matrix ----
        List<ClassAllocation> classAllocations = new ArrayList<>();
        int totalSections = 0, sectionsNoCt = 0, sectionsNoSubjects = 0;
        for (ClassResponse c : classes) {
            List<SectionAllocation> sectionAllocs = new ArrayList<>();
            for (SectionResponse s : c.sections()) {
                totalSections++;
                Person ct = s.classTeacherId() != null ? person(staffById.get(s.classTeacherId())) : null;
                if (ct == null) sectionsNoCt++;

                List<SubjectAllocation> subs = bySection.getOrDefault(s.id(), List.of()).stream()
                    .map(a -> new SubjectAllocation(
                        a.getSubjectId(),
                        subjectsById.containsKey(a.getSubjectId())
                            ? subjectsById.get(a.getSubjectId()).getName() : "(unknown subject)",
                        person(staffById.get(a.getStaffId()))))
                    .sorted((x, y) -> x.subjectName().compareToIgnoreCase(y.subjectName()))
                    .toList();
                if (subs.isEmpty()) sectionsNoSubjects++;

                sectionAllocs.add(new SectionAllocation(s.id(), s.name(), ct, subs));
            }
            classAllocations.add(new ClassAllocation(c.id(), c.name(), sectionAllocs));
        }

        // ---- Workload per teacher ----
        int workingDays = (int) entries.stream().map(TimetableEntry::getDayOfWeek).distinct().count();
        if (workingDays == 0) workingDays = 5;
        int periodsPerDay = teachingPeriods.size();
        int weeklyCapacity = periodsPerDay * workingDays;

        Map<UUID, Long> periodsByTeacher = entries.stream()
            .filter(e -> e.getTeacherId() != null)
            .collect(Collectors.groupingBy(TimetableEntry::getTeacherId, Collectors.counting()));

        List<TeacherWorkload> teachers = new ArrayList<>();
        int teachersWithoutLoad = 0;
        for (Staff st : staffById.values().stream()
                .filter(s -> TEACHING_ROLES.contains(s.getRole()))
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName())).toList()) {

            List<String> ctOf = classes.stream().flatMap(c -> c.sections().stream())
                .filter(s -> st.getId().equals(s.classTeacherId()))
                .map(s -> sectionLabel.getOrDefault(s.id(), s.name()))
                .toList();

            List<TeacherSubjectAssignment> mine = assignments.stream()
                .filter(a -> st.getId().equals(a.getStaffId())).toList();
            int subjectCount = (int) mine.stream().map(TeacherSubjectAssignment::getSubjectId).distinct().count();

            Set<UUID> sectionSet = new java.util.HashSet<>();
            mine.forEach(a -> sectionSet.add(a.getSectionId()));
            classes.stream().flatMap(c -> c.sections().stream())
                .filter(s -> st.getId().equals(s.classTeacherId()))
                .forEach(s -> sectionSet.add(s.id()));
            entries.stream().filter(e -> st.getId().equals(e.getTeacherId()))
                .forEach(e -> sectionSet.add(e.getSectionId()));

            int periodsPerWeek = periodsByTeacher.getOrDefault(st.getId(), 0L).intValue();
            int free = Math.max(0, weeklyCapacity - periodsPerWeek);
            int util = weeklyCapacity > 0 ? Math.round(periodsPerWeek * 100f / weeklyCapacity) : 0;

            if (periodsPerWeek == 0 && subjectCount == 0 && ctOf.isEmpty()) teachersWithoutLoad++;

            teachers.add(new TeacherWorkload(st.getId(), st.displayName(), st.getRole().name(),
                ctOf, subjectCount, sectionSet.size(), periodsPerWeek, free, weeklyCapacity, util));
        }

        // ---- Now teaching ----
        NowTeaching now = computeNow(entries, teachingPeriods, sectionLabel, subjectsById, staffById);

        Summary summary = new Summary(
            classes.size(), totalSections, teachers.size(),
            sectionsNoCt, sectionsNoSubjects, teachersWithoutLoad,
            assignments.size(), workingDays, periodsPerDay);

        return new TeacherAllocationOverviewResponse(summary, classAllocations, teachers, now);
    }

    private NowTeaching computeNow(List<TimetableEntry> entries, List<TimetablePeriod> periods,
                                   Map<UUID, String> sectionLabel, Map<UUID, Subject> subjectsById,
                                   Map<UUID, Staff> staffById) {
        int dow = LocalDate.now().getDayOfWeek().getValue();
        LocalTime nowTime = LocalTime.now();
        TimetablePeriod current = periods.stream()
            .filter(p -> p.getStartTime() != null && p.getEndTime() != null
                && !nowTime.isBefore(p.getStartTime()) && nowTime.isBefore(p.getEndTime()))
            .findFirst().orElse(null);
        if (current == null) {
            return new NowTeaching(false, null, null, null, List.of());
        }
        List<OngoingClass> ongoing = entries.stream()
            .filter(e -> e.getDayOfWeek() == dow && current.getId().equals(e.getPeriodId()) && e.getTeacherId() != null)
            .map(e -> new OngoingClass(
                sectionLabel.getOrDefault(e.getSectionId(), "—"),
                e.getSubjectId() != null && subjectsById.containsKey(e.getSubjectId())
                    ? subjectsById.get(e.getSubjectId()).getName() : "—",
                staffById.containsKey(e.getTeacherId()) ? staffById.get(e.getTeacherId()).displayName() : "—"))
            .sorted((a, b) -> a.sectionLabel().compareToIgnoreCase(b.sectionLabel()))
            .toList();
        return new NowTeaching(true, current.getName(),
            current.getStartTime().toString(), current.getEndTime().toString(), ongoing);
    }

    private static Person person(Staff s) {
        return s == null ? null : new Person(s.getId(), s.displayName(), s.getRole().name());
    }
}
