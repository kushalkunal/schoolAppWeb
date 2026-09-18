package in.schoolapp.attendance;

import in.schoolapp.attendance.dto.AttendanceEntryDto;
import in.schoolapp.attendance.dto.AttendanceRecordResponse;
import in.schoolapp.attendance.dto.AttendanceSectionResponse;
import in.schoolapp.attendance.dto.AttendanceSubmitResponse;
import in.schoolapp.attendance.dto.SubmitAttendanceRequest;
import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceSectionLock;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.event.AttendanceSubmittedEvent;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.attendance.repository.AttendanceSectionLockRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Submission follows LLD §5.2 "reverse marking": a section is PRESENT by default; teachers only
 * flag the exceptions (absent / late / half-day / leave). Any enrolled student the teacher does
 * not list is implicitly marked PRESENT. Idempotent per (student, date) via upsert — safe to
 * re-submit from the mobile offline sync queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceSectionLockRepository lockRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final ClassSectionService classSectionService;
    private final StaffRepository staffRepository;
    private final ApplicationEventPublisher events;
    private final in.schoolapp.calendar.SchoolCalendarService calendarService;
    private final in.schoolapp.timetable.repository.TimetableSubstitutionRepository substitutionRepository;

    @Transactional
    public AttendanceSubmitResponse submitAttendance(
        UUID tenantId, UUID sectionId, SubmitAttendanceRequest req
    ) {
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
        LocalDate date = req.date();
        UUID staffId = TenantContext.getStaffId();

        // RBAC: who may mark this section's attendance today —
        //   • PRINCIPAL / SCHOOL_OWNER / ADMIN — any section, any day;
        //   • the section's CLASS_TEACHER;
        //   • a substitute teacher with a substitution for this section today. Temporary rights are
        //     derived from the substitution
        //     — no manual permission grant — and expire automatically after the period ends.
        String role = TenantContext.getRole();
        boolean isPrincipalOrAdmin = "PRINCIPAL".equals(role) || "SCHOOL_OWNER".equals(role) || "ADMIN".equals(role);
        boolean isClassTeacher = staffId != null && staffId.equals(section.getClassTeacherId());
        if (!isPrincipalOrAdmin && !isClassTeacher
                && !hasActiveSubstitution(tenantId, sectionId, staffId, date)) {
            throw new AppException(ErrorCode.SECTION_NOT_ASSIGNED,
                "You are not authorized to mark attendance for this section right now");
        }

        // Calendar gate: the school must be open on this date (a configured working weekday and
        // not a holiday). Keeps attendance off Sundays/holidays per the per-tenant school calendar.
        if (!calendarService.isWorkingDay(tenantId, date)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "School is closed on " + date + " (non-working day or holiday). "
                    + "Adjust working days / holidays under Settings → Calendar if this is wrong.");
        }

        // Lock check: once a CLASS_TEACHER has submitted attendance, non-principal cannot re-submit.
        if (!isPrincipalOrAdmin && lockRepository.existsBySectionIdAndDate(sectionId, date)) {
            throw new AppException(ErrorCode.ATTENDANCE_ALREADY_SUBMITTED,
                "Attendance for this section on " + date + " has already been submitted and is locked");
        }

        // Snapshot of all ACTIVE enrollments in the section — set the baseline for defaults
        List<StudentEnrollment> roster = enrollmentRepository
            .findBySectionIdAndStatus(section.getId(), EnrollmentStatus.ACTIVE);
        if (roster.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Cannot submit attendance: section has no active students");
        }

        // Index submitted entries by studentId for O(1) lookup
        Map<UUID, AttendanceEntryDto> overrides = new HashMap<>();
        for (AttendanceEntryDto e : req.entries()) {
            overrides.put(e.studentId(), e);
        }

        // Validate all submitted studentIds are in this section's roster (prevents cross-tenant
        // injection via a JWT that only has access to a different section)
        for (UUID submittedId : overrides.keySet()) {
            boolean inRoster = roster.stream().anyMatch(r -> r.getStudentId().equals(submittedId));
            if (!inRoster) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Student " + submittedId + " is not enrolled in this section");
            }
        }

        List<AttendanceRecord> upserted = new ArrayList<>();
        int presentCount = 0, absentCount = 0, lateCount = 0, halfCount = 0, leaveCount = 0;
        List<AttendanceRecord> absentRecords = new ArrayList<>();
        List<AttendanceRecord> lateRecords = new ArrayList<>();

        for (StudentEnrollment enr : roster) {
            AttendanceEntryDto override = overrides.get(enr.getStudentId());
            AttendanceStatus status = override != null ? override.status() : AttendanceStatus.PRESENT;

            AttendanceRecord record = attendanceRepository
                .findByStudentIdAndDate(enr.getStudentId(), date)
                .orElseGet(() -> {
                    AttendanceRecord r = new AttendanceRecord();
                    r.setSchoolId(tenantId);
                    r.setStudentId(enr.getStudentId());
                    r.setSectionId(section.getId());
                    r.setDate(date);
                    return r;
                });
            record.setStatus(status);
            record.setArrivalTime(override != null ? override.arrivalTime() : null);
            record.setNote(override != null ? override.note() : null);
            record.setMarkedById(staffId);
            record = attendanceRepository.save(record);
            upserted.add(record);

            switch (status) {
                case PRESENT  -> presentCount++;
                case ABSENT   -> { absentCount++; absentRecords.add(record); }
                case LATE     -> { lateCount++; lateRecords.add(record); }
                case HALF_DAY -> halfCount++;
                case LEAVE    -> leaveCount++;
            }
        }

        // Fire-and-forget: notifications dispatch asynchronously
        events.publishEvent(new AttendanceSubmittedEvent(
            tenantId, section.getId(), date, absentRecords, lateRecords, staffId
        ));

        log.info("Attendance submitted tenantId={} sectionId={} date={} present={} absent={} late={}",
            tenantId, section.getId(), date, presentCount, absentCount, lateCount);

        // Lock when a non-admin submits — the section's class teacher OR an authorized substitute
        // (both are the only non-admins who get here). Records submittedBy = who marked, so the lock
        // doubles as the "attendance marked by" audit. Principals/admins override without re-locking.
        if (!isPrincipalOrAdmin) {
            AttendanceSectionLock lock = lockRepository
                .findBySectionIdAndDate(sectionId, date)
                .orElseGet(() -> {
                    AttendanceSectionLock l = new AttendanceSectionLock();
                    l.setSchoolId(tenantId);
                    l.setSectionId(sectionId);
                    l.setDate(date);
                    return l;
                });
            lock.setSubmittedBy(staffId);
            lock.setSubmittedAt(Instant.now());
            lockRepository.save(lock);
        }

        return new AttendanceSubmitResponse(
            date, section.getId(),
            upserted.size(), presentCount, absentCount, lateCount, halfCount, leaveCount,
            upserted.stream().map(AttendanceRecordResponse::from).toList(),
            absentRecords.size() + lateRecords.size()
        );
    }

    /**
     * Records a single attendance entry migrated from a paper register — called by the
     * migration commit flow. Idempotent upsert on {@code (studentId, date)} so re-running a
     * migration after correcting the review grid doesn't create duplicates. No event is
     * published because historical alerts would be noise to parents.
     */
    @Transactional
    public AttendanceRecord createHistorical(UUID tenantId, UUID studentId, UUID sectionId,
                                             LocalDate date, AttendanceStatus status, String note) {
        AttendanceRecord record = attendanceRepository.findByStudentIdAndDate(studentId, date)
            .orElseGet(() -> {
                AttendanceRecord r = new AttendanceRecord();
                r.setSchoolId(tenantId);
                r.setStudentId(studentId);
                r.setSectionId(sectionId);
                r.setDate(date);
                return r;
            });
        record.setStatus(status);
        record.setNote(note);
        record.setHistorical(true);
        return attendanceRepository.save(record);
    }

    @Transactional(readOnly = true)
    public AttendanceSectionResponse getSectionAttendance(
        UUID tenantId, UUID sectionId, LocalDate date
    ) {
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);

        // CLASS_TEACHER may only view their own section's attendance.
        String role = TenantContext.getRole();
        if ("CLASS_TEACHER".equals(role)) {
            UUID staffId = TenantContext.getStaffId();
            if (!staffId.equals(section.getClassTeacherId())) {
                throw new AppException(ErrorCode.SECTION_NOT_ASSIGNED,
                    "You are not the class teacher of this section");
            }
        }

        List<AttendanceRecordResponse> records = attendanceRepository
            .findBySchoolIdAndSectionIdAndDate(tenantId, sectionId, date)
            .stream()
            .map(AttendanceRecordResponse::from)
            .toList();

        // Lock info
        Optional<AttendanceSectionLock> lockOpt = lockRepository.findBySectionIdAndDate(sectionId, date);
        boolean locked = lockOpt.isPresent();
        String lockedByName = null;
        Instant lockedAt = null;
        if (locked) {
            AttendanceSectionLock lock = lockOpt.get();
            lockedAt = lock.getSubmittedAt();
            lockedByName = staffRepository.findById(lock.getSubmittedBy())
                .map(s -> s.getFirstName() + (s.getLastName() != null ? " " + s.getLastName() : ""))
                .orElse("Unknown");
        }

        return new AttendanceSectionResponse(sectionId, date.toString(), locked, lockedByName, lockedAt, records);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getStudentHistory(
        UUID tenantId, UUID studentId, LocalDate from, LocalDate to
    ) {
        return attendanceRepository
            .findByStudentIdAndDateBetweenOrderByDateDesc(studentId, from, to)
            .stream()
            .filter(r -> r.getSchoolId().equals(tenantId))
            .map(AttendanceRecordResponse::from)
            .toList();
    }

    /**
     * True when {@code staffId} holds an active substitution for {@code sectionId} today: any
     * substitution row for this section + date where they are the substitute. The temporary
     * attendance right is granted for the whole school day of the substitution — daily attendance
     * is a single per-section/day record (locked once submitted, audited via {@code submittedBy}),
     * so it cannot be tied to a single period's minute-window. The right is automatic (derived from
     * the substitution, no manual grant) and auto-expires the next day.
     */
    private boolean hasActiveSubstitution(UUID tenantId, UUID sectionId, UUID staffId, LocalDate date) {
        if (staffId == null || !date.equals(LocalDate.now())) return false;   // today only
        return substitutionRepository.findBySubstituteTeacherIdAndDate(staffId, date).stream()
            .anyMatch(sub -> sectionId.equals(sub.getSectionId()) && tenantId.equals(sub.getSchoolId()));
    }
}
