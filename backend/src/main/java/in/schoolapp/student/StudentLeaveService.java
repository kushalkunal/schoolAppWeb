package in.schoolapp.student;

import in.schoolapp.attendance.AttendanceService;
import in.schoolapp.attendance.dto.AttendanceEntryDto;
import in.schoolapp.attendance.dto.SubmitAttendanceRequest;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.dto.StudentLeaveDto;
import in.schoolapp.student.entity.StudentLeaveApplication;
import in.schoolapp.student.entity.StudentLeaveApplication.LeaveStatus;
import in.schoolapp.student.repository.StudentLeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Student leave workflow:
 * <pre>
 *   submit()  → SUBMITTED  (teacher or parent applies)
 *   decide(approve=true)  → APPROVED  (auto-writes attendance as LEAVE for each day)
 *   decide(approve=false) → REJECTED
 *   cancel()  → CANCELLED
 * </pre>
 *
 * <p><b>RBAC</b>:
 * <ul>
 *   <li>Submit: {@code CLASS_TEACHER} of that section or {@code ADMIN/PRINCIPAL/OWNER}.</li>
 *   <li>Decide: {@code CLASS_TEACHER} of that section or {@code ADMIN/PRINCIPAL/OWNER}.</li>
 *   <li>View pending: CLASS_TEACHER sees own section; ADMIN sees all.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentLeaveService {

    private final StudentLeaveRepository leaveRepository;
    private final ClassSectionService classSectionService;
    private final AttendanceService attendanceService;

    @Transactional
    public StudentLeaveDto submit(UUID tenantId, StudentLeaveDto req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "end_date must be on or after start_date");
        }

        Section section = classSectionService.getSectionOrThrow(tenantId, req.sectionId());

        // RBAC: CLASS_TEACHER may only submit leave for their own section.
        String role = TenantContext.getRole();
        boolean isAdmin = "PRINCIPAL".equals(role) || "ADMIN".equals(role) || "SCHOOL_OWNER".equals(role);
        if ("CLASS_TEACHER".equals(role) && !isAdmin) {
            UUID selfId = TenantContext.getStaffId();
            if (!selfId.equals(section.getClassTeacherId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                    "You can only submit leave for students in your own section.");
            }
        }

        StudentLeaveApplication app = new StudentLeaveApplication();
        app.setSchoolId(tenantId);
        app.setStudentId(req.studentId());
        app.setSectionId(req.sectionId());
        app.setStartDate(req.startDate());
        app.setEndDate(req.endDate());
        app.setDays(req.days());
        app.setReason(req.reason());
        app.setAppliedByStaffId(TenantContext.getStaffId());
        app.setStatus(LeaveStatus.SUBMITTED);
        return StudentLeaveDto.from(leaveRepository.save(app));
    }

    @Transactional
    public StudentLeaveDto decide(UUID tenantId, UUID applicationId, boolean approve, String note) {
        StudentLeaveApplication app = leaveRepository.findByIdAndSchoolId(applicationId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Student leave application not found"));

        if (app.getStatus() != LeaveStatus.SUBMITTED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Only SUBMITTED applications can be decided (current: " + app.getStatus() + ")");
        }

        Section section = classSectionService.getSectionOrThrow(tenantId, app.getSectionId());

        // RBAC: CLASS_TEACHER may only decide for their own section.
        String role = TenantContext.getRole();
        boolean isAdmin = "PRINCIPAL".equals(role) || "ADMIN".equals(role) || "SCHOOL_OWNER".equals(role);
        if ("CLASS_TEACHER".equals(role) && !isAdmin) {
            UUID selfId = TenantContext.getStaffId();
            if (!selfId.equals(section.getClassTeacherId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                    "You can only decide leave for students in your own section.");
            }
        }

        app.setStatus(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        app.setDecidedByStaffId(TenantContext.getStaffId());
        app.setDecisionNote(note);
        app.setDecidedAt(OffsetDateTime.now());
        leaveRepository.save(app);

        // On approval, auto-write attendance as LEAVE for each day of the leave period.
        if (approve) {
            writeLeaveAttendance(tenantId, app);
        }

        return StudentLeaveDto.from(app);
    }

    @Transactional
    public StudentLeaveDto cancel(UUID tenantId, UUID applicationId) {
        StudentLeaveApplication app = leaveRepository.findByIdAndSchoolId(applicationId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Student leave application not found"));
        if (app.getStatus() == LeaveStatus.CANCELLED) return StudentLeaveDto.from(app);
        app.setStatus(LeaveStatus.CANCELLED);
        app.setDecidedAt(OffsetDateTime.now());
        return StudentLeaveDto.from(leaveRepository.save(app));
    }

    @Transactional(readOnly = true)
    public List<StudentLeaveDto> listForStudent(UUID tenantId, UUID studentId) {
        return leaveRepository.findByStudentIdOrderByStartDateDesc(studentId).stream()
            .filter(a -> a.getSchoolId().equals(tenantId))
            .map(StudentLeaveDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentLeaveDto> listPendingForSection(UUID tenantId, UUID sectionId) {
        classSectionService.getSectionOrThrow(tenantId, sectionId);

        // RBAC: CLASS_TEACHER can only see their own section's pending leaves.
        String role = TenantContext.getRole();
        boolean isAdmin = "PRINCIPAL".equals(role) || "ADMIN".equals(role) || "SCHOOL_OWNER".equals(role);
        if ("CLASS_TEACHER".equals(role) && !isAdmin) {
            Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
            UUID selfId = TenantContext.getStaffId();
            if (!selfId.equals(section.getClassTeacherId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                    "You can only view pending leaves for your own section.");
            }
        }

        return leaveRepository.findBySectionIdAndStatus(sectionId, LeaveStatus.SUBMITTED).stream()
            .filter(a -> a.getSchoolId().equals(tenantId))
            .map(StudentLeaveDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentLeaveDto> listAllPending(UUID tenantId) {
        return leaveRepository.findBySchoolIdAndStatusOrderByCreatedAtDesc(tenantId, LeaveStatus.SUBMITTED).stream()
            .map(StudentLeaveDto::from).toList();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Writes a LEAVE attendance record for every date between startDate and endDate (inclusive).
     * Uses AttendanceService in a best-effort way — failures are logged but do not roll back
     * the approval.
     */
    private void writeLeaveAttendance(UUID tenantId, StudentLeaveApplication app) {
        LocalDate date = app.getStartDate();
        while (!date.isAfter(app.getEndDate())) {
            try {
                SubmitAttendanceRequest req = new SubmitAttendanceRequest(
                    date,
                    List.of(new AttendanceEntryDto(app.getStudentId(), AttendanceStatus.LEAVE, null, "Approved leave"))
                );
                attendanceService.submitAttendance(tenantId, app.getSectionId(), req);
            } catch (Exception ex) {
                log.warn("Could not write LEAVE attendance for student={} date={}: {}",
                    app.getStudentId(), date, ex.getMessage());
            }
            date = date.plusDays(1);
        }
    }
}
