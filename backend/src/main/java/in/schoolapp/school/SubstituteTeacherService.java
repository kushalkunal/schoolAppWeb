package in.schoolapp.school;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.school.dto.CreateSubstituteRequest;
import in.schoolapp.school.dto.SubstituteResponse;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.SubstituteAssignment;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.school.repository.SubstituteAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily substitute teacher workflow (gap §5.5). Three operations are enough for the typical
 * morning flow:
 * <ul>
 *   <li>Assign a substitute for a specific section + date</li>
 *   <li>List today's (or a date's) assignments for the principal dashboard</li>
 *   <li>Cancel an assignment (before or after the day) — preserves the row via hard-delete
 *       because it's just a one-day scheduling record; audit log captures who/when.</li>
 * </ul>
 * Assignment also WhatsApps the substitute so they find out before homeroom.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubstituteTeacherService {

    private final SubstituteAssignmentRepository repository;
    private final StaffRepository staffRepository;
    private final ClassSectionService classSectionService;
    private final WhatsAppNotifier whatsAppNotifier;
    private final AuditLogger auditLogger;

    @Transactional
    public SubstituteResponse assign(UUID tenantId, CreateSubstituteRequest req) {
        if (req.absentTeacherId().equals(req.substituteId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Absent teacher and substitute cannot be the same person");
        }
        Section section = classSectionService.getSectionOrThrow(tenantId, req.sectionId());
        Staff substitute = requireStaffInTenant(tenantId, req.substituteId());
        requireStaffInTenant(tenantId, req.absentTeacherId());

        if (repository.findBySchoolIdAndSectionIdAndAssignedDate(
                tenantId, section.getId(), req.assignedDate()).isPresent()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "A substitute is already assigned for this section on this date");
        }

        SubstituteAssignment a = new SubstituteAssignment();
        a.setSchoolId(tenantId);
        a.setAbsentTeacherId(req.absentTeacherId());
        a.setSubstituteId(req.substituteId());
        a.setSectionId(section.getId());
        a.setAssignedDate(req.assignedDate());
        a.setNote(req.note());
        a.setCreatedById(TenantContext.getStaffId());
        a = repository.save(a);
        log.info("Assigned substitute id={} tenant={} section={} date={}",
            a.getId(), tenantId, section.getId(), req.assignedDate());

        auditLogger.logCreate(tenantId, "SubstituteAssignment", a.getId(), Map.of(
            "absentTeacherId", a.getAbsentTeacherId(),
            "substituteId", a.getSubstituteId(),
            "sectionId", a.getSectionId(),
            "assignedDate", a.getAssignedDate().toString()
        ));

        notifySubstitute(substitute, section, req.assignedDate(), req.note());
        return SubstituteResponse.from(a);
    }

    @Transactional(readOnly = true)
    public List<SubstituteResponse> listForDate(UUID tenantId, LocalDate date) {
        return repository.findBySchoolIdAndAssignedDateOrderByCreatedAtAsc(tenantId, date).stream()
            .map(SubstituteResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SubstituteResponse> listForTeacherOnDate(UUID tenantId, UUID substituteId, LocalDate date) {
        return repository.findBySubstituteIdAndAssignedDateOrderByCreatedAtAsc(substituteId, date).stream()
            .filter(a -> a.getSchoolId().equals(tenantId))
            .map(SubstituteResponse::from)
            .toList();
    }

    @Transactional
    public void cancel(UUID tenantId, UUID assignmentId) {
        SubstituteAssignment a = repository.findByIdAndSchoolId(assignmentId, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "SubstituteAssignment", assignmentId));
        repository.delete(a);
        log.info("Cancelled substitute assignment id={} tenant={}", assignmentId, tenantId);
        auditLogger.logDelete(tenantId, "SubstituteAssignment", assignmentId, Map.of(
            "sectionId", a.getSectionId(),
            "substituteId", a.getSubstituteId(),
            "assignedDate", a.getAssignedDate().toString()
        ));
    }
    
    private Staff requireStaffInTenant(UUID tenantId, UUID staffId) {
        Staff s = staffRepository.findById(staffId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId));
        if (!s.getSchoolId().equals(tenantId)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId);
        }
        return s;
    }

    private void notifySubstitute(Staff substitute, Section section, LocalDate date, String note) {
        if (substitute.getPhone() == null || substitute.getPhone().isBlank()) return;
        String body = String.format(
            "Hi %s, you've been assigned as substitute for section %s on %s.%s",
            substitute.getFirstName(), section.getName(), date,
            (note == null || note.isBlank()) ? "" : "\n\nNote: " + note);
        whatsAppNotifier.send(WhatsAppMessage.text(
            substitute.getPhone(), body, MessageType.EMERGENCY,
            WhatsAppMessage.Audit.forSchool(substitute.getSchoolId())));
    }
}
