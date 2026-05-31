package in.schoolapp.timetable;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.timetable.dto.PeriodDto;
import in.schoolapp.timetable.dto.SubstituteCandidatesResponse;
import in.schoolapp.timetable.dto.SubstitutionDto;
import in.schoolapp.timetable.dto.TimetableEntryDto;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetablePeriodRepository periodRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimetableSubstitutionRepository substitutionRepository;
    private final StaffRepository staffRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;

    /** Roles that can stand in for an absent teacher. */
    private static final Set<StaffRole> TEACHING_ROLES =
        EnumSet.of(StaffRole.CLASS_TEACHER, StaffRole.SUBJECT_TEACHER, StaffRole.PRINCIPAL);
    /** Staff-attendance states that mean the teacher is unavailable today. */
    private static final Set<StaffAttendance.StaffAttendanceStatus> ABSENT_STATES =
        EnumSet.of(StaffAttendance.StaffAttendanceStatus.ABSENT,
                   StaffAttendance.StaffAttendanceStatus.LEAVE);

    // ---------- Periods ----------

    @Transactional(readOnly = true)
    public List<PeriodDto> listPeriods(UUID tenantId) {
        return periodRepository.findBySchoolIdOrderBySortOrderAsc(tenantId).stream()
            .map(PeriodDto::from).toList();
    }

    @Transactional
    public PeriodDto createPeriod(UUID tenantId, PeriodDto req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "endTime must be after startTime");
        }
        TimetablePeriod p = new TimetablePeriod();
        p.setSchoolId(tenantId);
        p.setName(req.name());
        p.setStartTime(req.startTime());
        p.setEndTime(req.endTime());
        p.setSortOrder(req.sortOrder());
        p.setBreakSlot(req.breakSlot());
        return PeriodDto.from(periodRepository.save(p));
    }

    @Transactional
    public void deletePeriod(UUID tenantId, UUID periodId) {
        TimetablePeriod p = periodRepository.findById(periodId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Period", periodId));
        if (!p.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Period not in this school");
        }
        periodRepository.delete(p);
    }

    // ---------- Entries ----------

    @Transactional(readOnly = true)
    public List<TimetableEntryDto> getSectionTimetable(UUID tenantId, UUID sectionId) {
        // Tenant scope check happens at the controller via TenantInterceptor; entries
        // inherit their section's school binding via BaseEntity.schoolId on insert.
        return entryRepository.findBySectionIdOrderByDayOfWeekAsc(sectionId).stream()
            .filter(e -> e.getSchoolId().equals(tenantId))
            .map(TimetableEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TimetableEntryDto> getTeacherTimetable(UUID tenantId, UUID teacherId) {
        return entryRepository.findByTeacherId(teacherId).stream()
            .filter(e -> e.getSchoolId().equals(tenantId))
            .map(TimetableEntryDto::from).toList();
    }

    @Transactional
    public TimetableEntryDto upsertEntry(UUID tenantId, TimetableEntryDto req) {
        TimetableEntry e;
        if (req.id() != null) {
            e = entryRepository.findById(req.id())
                .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TimetableEntry", req.id()));
            if (!e.getSchoolId().equals(tenantId)) {
                throw new AppException(ErrorCode.FORBIDDEN, "Entry not in this school");
            }
        } else {
            e = new TimetableEntry();
            e.setSchoolId(tenantId);
            e.setSectionId(req.sectionId());
            e.setPeriodId(req.periodId());
            e.setDayOfWeek(req.dayOfWeek());
        }

        // Conflict detection: a teacher cannot be in two sections at the same period+day.
        if (req.teacherId() != null) {
            UUID teacherId = req.teacherId();
            UUID excludeId = e.getId();  // null on create; non-null on update (exclude self)
            boolean conflict = entryRepository
                .findByTeacherIdAndDayOfWeek(teacherId, req.dayOfWeek())
                .stream()
                .anyMatch(existing ->
                    existing.getPeriodId().equals(req.periodId())
                    && !existing.getSectionId().equals(req.sectionId())
                    && (excludeId == null || !existing.getId().equals(excludeId))
                );
            if (conflict) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Teacher is already assigned to another class in this period on this day");
            }
        }

        e.setSubjectId(req.subjectId());
        e.setTeacherId(req.teacherId());
        e.setNote(req.note());
        return TimetableEntryDto.from(entryRepository.save(e));
    }

    @Transactional
    public void deleteEntry(UUID tenantId, UUID entryId) {
        TimetableEntry e = entryRepository.findById(entryId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TimetableEntry", entryId));
        if (!e.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Entry not in this school");
        }
        entryRepository.delete(e);
    }

    // ---------- Today's classes (teacher dashboard) ----------

    /**
     * Returns the timetable entries for a teacher on today's day-of-week, overlaid with any
     * substitution assignments for today. Useful for the "Today's classes" teacher dashboard
     * widget.
     */
    @Transactional(readOnly = true)
    public List<TimetableEntryDto> getTodayForTeacher(UUID tenantId, UUID teacherId) {
        int todayDow = LocalDate.now().getDayOfWeek().getValue(); // 1=Mon…7=Sun
        List<TimetableEntry> regular = entryRepository
            .findByTeacherIdAndDayOfWeek(teacherId, todayDow).stream()
            .filter(e -> e.getSchoolId().equals(tenantId))
            .toList();
        // Also include substitute slots where this teacher is covering today
        List<TimetableSubstitution> substituting = substitutionRepository
            .findBySubstituteTeacherIdAndDate(teacherId, LocalDate.now());
        List<TimetableEntryDto> result = new java.util.ArrayList<>(regular.stream()
            .map(TimetableEntryDto::from).toList());
        // Add substitution entries as synthetic TimetableEntryDto rows (teacherId = substitute)
        for (TimetableSubstitution sub : substituting) {
            if (sub.getSchoolId().equals(tenantId)) {
                result.add(new TimetableEntryDto(
                    sub.getId(), sub.getSectionId(), sub.getPeriodId(),
                    todayDow, null, sub.getSubstituteTeacherId(),
                    "[Substitution] " + (sub.getReason() != null ? sub.getReason() : "")));
            }
        }
        return result;
    }

    // ---------- Substitute teacher assignment ----------

    /**
     * Assigns a substitute teacher for a specific section × period × date.
     * Only one substitute per slot per day — duplicate call replaces the previous assignment.
     * Validates that the substitute is not already teaching another section at that period.
     */
    @Transactional
    public SubstitutionDto assignSubstitute(UUID tenantId, SubstitutionDto req) {
        // Conflict check: substitute cannot cover two sections on the same period+date.
        List<TimetableSubstitution> existing = substitutionRepository
            .findBySubstituteTeacherIdAndDate(req.substituteTeacherId(), req.date());
        int todayDow = req.date().getDayOfWeek().getValue();
        boolean conflict = existing.stream().anyMatch(s ->
            s.getPeriodId().equals(req.periodId())
            && !s.getSectionId().equals(req.sectionId())
            && s.getSchoolId().equals(tenantId));
        // Also check regular timetable for the substitute on that day
        if (!conflict) {
            conflict = entryRepository
                .findByTeacherIdAndDayOfWeek(req.substituteTeacherId(), todayDow)
                .stream()
                .anyMatch(e -> e.getPeriodId().equals(req.periodId())
                    && !e.getSectionId().equals(req.sectionId())
                    && e.getSchoolId().equals(tenantId));
        }
        if (conflict) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Substitute teacher is already assigned to another class in this period");
        }

        TimetableSubstitution sub = substitutionRepository
            .findBySectionIdAndPeriodIdAndDate(req.sectionId(), req.periodId(), req.date())
            .orElseGet(() -> {
                TimetableSubstitution s = new TimetableSubstitution();
                s.setSchoolId(tenantId);
                s.setSectionId(req.sectionId());
                s.setPeriodId(req.periodId());
                s.setDate(req.date());
                return s;
            });
        sub.setAbsentTeacherId(req.absentTeacherId());
        sub.setSubstituteTeacherId(req.substituteTeacherId());
        sub.setReason(req.reason());
        return SubstitutionDto.from(substitutionRepository.save(sub));
    }

    /**
     * Returns all substitutions for a given date, scoped to the school.
     * Used by the principal's daily operations view.
     */
    @Transactional(readOnly = true)
    public List<SubstitutionDto> listSubstitutionsForDate(UUID tenantId, LocalDate date) {
        return substitutionRepository.findByDateAndSchoolId(date, tenantId).stream()
            .map(SubstitutionDto::from).toList();
    }

    /**
     * Decision-support for assigning a substitute to one period on one date. Splits teaching
     * staff into: absent today (candidates to replace), free at this slot (best substitutes),
     * and busy at this slot (already teaching/substituting). This turns the previously-blind
     * "pick any teacher" dropdown into an availability-aware picker.
     *
     * @param sectionId optional — the section being covered; a teacher already teaching THIS
     *                  section at this slot is the one being replaced, not "busy elsewhere".
     */
    @Transactional(readOnly = true)
    public SubstituteCandidatesResponse getSubstituteCandidates(
            UUID tenantId, LocalDate date, UUID periodId, UUID sectionId) {
        int dow = date.getDayOfWeek().getValue();

        // Who is unavailable today (marked ABSENT or on LEAVE).
        Set<UUID> absentIds = staffAttendanceRepository
            .findBySchoolIdAndAttendanceDate(tenantId, date).stream()
            .filter(a -> ABSENT_STATES.contains(a.getStatus()))
            .map(StaffAttendance::getStaffId)
            .collect(Collectors.toSet());

        // Who is teaching some OTHER section at this period today (regular timetable).
        Map<UUID, UUID> busyTeacherToSection = entryRepository.findByPeriodIdAndDayOfWeek(periodId, dow).stream()
            .filter(e -> tenantId.equals(e.getSchoolId()) && e.getTeacherId() != null
                && !e.getSectionId().equals(sectionId))
            .collect(Collectors.toMap(TimetableEntry::getTeacherId, TimetableEntry::getSectionId, (a, b) -> a));

        // Who is already covering another section as a substitute at this period today.
        Set<UUID> substitutingIds = substitutionRepository.findByDateAndSchoolId(date, tenantId).stream()
            .filter(s -> s.getPeriodId().equals(periodId) && !s.getSectionId().equals(sectionId))
            .map(TimetableSubstitution::getSubstituteTeacherId)
            .collect(Collectors.toSet());

        List<SubstituteCandidatesResponse.Candidate> absent = new ArrayList<>();
        List<SubstituteCandidatesResponse.Candidate> available = new ArrayList<>();
        List<SubstituteCandidatesResponse.Candidate> busy = new ArrayList<>();

        for (Staff s : staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(tenantId)) {
            if (!TEACHING_ROLES.contains(s.getRole())) continue;
            String role = s.getRole().name();
            if (absentIds.contains(s.getId())) {
                absent.add(candidate(s, role, "Marked absent today"));
            } else if (busyTeacherToSection.containsKey(s.getId())) {
                busy.add(candidate(s, role, "Teaching another class this period"));
            } else if (substitutingIds.contains(s.getId())) {
                busy.add(candidate(s, role, "Already substituting this period"));
            } else {
                available.add(candidate(s, role, "Free this period"));
            }
        }
        return new SubstituteCandidatesResponse(absent, available, busy);
    }

    private static SubstituteCandidatesResponse.Candidate candidate(Staff s, String role, String note) {
        return new SubstituteCandidatesResponse.Candidate(s.getId(), s.displayName(), role, note);
    }
}
