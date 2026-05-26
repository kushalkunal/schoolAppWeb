package in.schoolapp.attendance;

import in.schoolapp.attendance.dto.AttendanceEntryDto;
import in.schoolapp.attendance.dto.AttendanceRecordResponse;
import in.schoolapp.attendance.dto.AttendanceSubmitResponse;
import in.schoolapp.attendance.dto.SubmitAttendanceRequest;
import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.event.AttendanceSubmittedEvent;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final StudentEnrollmentRepository enrollmentRepository;
    private final ClassSectionService classSectionService;
    private final ApplicationEventPublisher events;

    @Transactional
    public AttendanceSubmitResponse submitAttendance(
        UUID tenantId, UUID sectionId, SubmitAttendanceRequest req
    ) {
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
        LocalDate date = req.date();
        UUID staffId = TenantContext.getStaffId();

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
    public List<AttendanceRecordResponse> getSectionAttendance(
        UUID tenantId, UUID sectionId, LocalDate date
    ) {
        classSectionService.getSectionOrThrow(tenantId, sectionId);
        return attendanceRepository.findBySchoolIdAndSectionIdAndDate(tenantId, sectionId, date)
            .stream()
            .map(AttendanceRecordResponse::from)
            .toList();
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
}
