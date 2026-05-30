package in.schoolapp.incident;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.parent.ParentNotificationService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.incident.dto.CreateIncidentRequest;
import in.schoolapp.incident.dto.IncidentResponse;
import in.schoolapp.incident.entity.Incident;
import in.schoolapp.student.StudentAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository repo;
    private final ParentNotificationService parentNotificationService;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional
    public IncidentResponse create(UUID tenantId, CreateIncidentRequest req) {
        studentAccessGuard.assertInTenant(tenantId, req.studentId());
        Incident i = new Incident();
        i.setSchoolId(tenantId);
        i.setStudentId(req.studentId());
        i.setSeverity(req.severity());
        i.setIncidentType(req.incidentType());
        i.setPoints(req.points());
        i.setOccurredOn(req.occurredOn());
        i.setDescription(req.description());
        i.setActionTaken(req.actionTaken());
        i.setReportedById(TenantContext.getStaffId());
        i.setParentNotified(req.notifyParent());
        Incident saved = repo.save(i);

        // Slice 34 — major / severe incidents push to parents (notifyParent flag honored).
        // The notification dispatcher checks PARENT_NOTIFY_CIRCULAR feature flag; we reuse
        // that channel since incidents are conceptually a one-off notice. Schools that
        // don't want notify-on-incident leave the flag off.
        if (req.notifyParent()) {
            String body = String.format(
                "*%s incident reported*\n\nType: %s · Points: %+d\nWhen: %s\n\n%s\n\n%s",
                req.severity(), req.incidentType(), req.points(),
                req.occurredOn(),
                req.description(),
                req.actionTaken() != null ? "Action: " + req.actionTaken() : "");
            parentNotificationService.notify(
                tenantId, req.studentId(),
                FeatureKey.PARENT_NOTIFY_CIRCULAR,
                req.severity() + " incident notice", body, MessageType.CIRCULAR);
        }
        return IncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> forStudent(UUID tenantId, UUID studentId) {
        studentAccessGuard.assertInTenant(tenantId, studentId);
        return repo.findByStudentIdOrderByOccurredOnDesc(studentId).stream()
            .map(IncidentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(UUID tenantId, int page, int size) {
        return repo.findBySchoolIdOrderByOccurredOnDesc(tenantId, PageRequest.of(page, size))
            .map(IncidentResponse::from);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        Incident i = repo.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Incident", id));
        repo.delete(i);
    }
}
