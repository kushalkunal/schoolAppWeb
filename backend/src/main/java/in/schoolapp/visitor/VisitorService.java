package in.schoolapp.visitor;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.common.TenantContext;
import in.schoolapp.student.StudentAccessGuard;
import in.schoolapp.student.repository.ParentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import in.schoolapp.visitor.dto.CreateVisitorRequest;
import in.schoolapp.visitor.dto.VisitorResponse;
import in.schoolapp.visitor.entity.Visitor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitorService {

    private final VisitorRepository repo;
    private final StudentAccessGuard studentAccessGuard;
    private final ParentRepository parentRepository;
    private final StudentParentLinkRepository linkRepository;
    private final AuditLogger audit;

    @Transactional
    public VisitorResponse checkIn(UUID tenantId, CreateVisitorRequest req) {
        Visitor v = new Visitor();
        v.setSchoolId(tenantId);
        v.setName(req.name());
        v.setPhone(req.phone());
        v.setPurpose(req.purpose());
        v.setHostStaffId(req.hostStaffId());
        v.setHostStudentId(req.hostStudentId());
        v.setStudentPickup(req.studentPickup());
        v.setBadgeNumber(req.badgeNumber());
        v.setPhotoUrl(req.photoUrl());
        v.setNotes(req.notes());
        v.setCreatedById(TenantContext.getStaffId());

        if (req.studentPickup()) {
            authorizePickup(tenantId, req, v);
        }

        Visitor saved = repo.save(v);

        if (req.studentPickup()) {
            audit.logAction(tenantId, "Visitor", saved.getId(),
                Boolean.TRUE.equals(saved.getPickupAuthorized()) ? "PICKUP_AUTHORIZED" : "PICKUP_OVERRIDE",
                Map.of("studentId", String.valueOf(req.hostStudentId()),
                    "visitor", req.name(),
                    "overrideReason", String.valueOf(saved.getPickupOverrideReason())));
        }
        return VisitorResponse.from(saved);
    }

    /**
     * Child-safety gate (audit #16): a student pickup must be by a registered guardian — a parent
     * whose phone is linked to that student. If not, the gate user must supply an override reason,
     * which is recorded (and audited by the caller). Throws otherwise.
     */
    private void authorizePickup(UUID tenantId, CreateVisitorRequest req, Visitor v) {
        if (req.hostStudentId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "hostStudentId is required for a student pickup.");
        }
        studentAccessGuard.assertInTenant(tenantId, req.hostStudentId());

        if (isRegisteredGuardian(tenantId, req.hostStudentId(), req.phone())) {
            v.setPickupAuthorized(true);
            return;
        }
        if (req.pickupOverrideReason() == null || req.pickupOverrideReason().isBlank()) {
            throw new AppException(ErrorCode.PICKUP_NOT_AUTHORIZED,
                "Visitor is not a registered guardian for this student. "
                    + "Provide an override reason to proceed.");
        }
        v.setPickupAuthorized(false);
        v.setPickupOverrideReason(req.pickupOverrideReason());
    }

    private boolean isRegisteredGuardian(UUID tenantId, UUID studentId, String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String normalized;
        try {
            normalized = PhoneNormalizer.normalize(phone);
        } catch (AppException e) {
            return false;   // unparseable phone can't be matched
        }
        return parentRepository.findBySchoolIdAndPhone(tenantId, normalized)
            .map(parent -> linkRepository.findByStudentId(studentId).stream()
                .anyMatch(link -> link.getParentId().equals(parent.getId())))
            .orElse(false);
    }

    @Transactional
    public VisitorResponse checkOut(UUID tenantId, UUID visitorId) {
        Visitor v = repo.findByIdAndSchoolId(visitorId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Visitor", visitorId));
        if (v.getOutAt() == null) {
            v.setOutAt(OffsetDateTime.now());
            repo.save(v);
        }
        return VisitorResponse.from(v);
    }

    @Transactional(readOnly = true)
    public Page<VisitorResponse> list(UUID tenantId, int page, int size) {
        return repo.findBySchoolIdOrderByInAtDesc(tenantId, PageRequest.of(page, size))
            .map(VisitorResponse::from);
    }

    @Transactional(readOnly = true)
    public List<VisitorResponse> listOpen(UUID tenantId) {
        return repo.findBySchoolIdAndOutAtIsNullOrderByInAtDesc(tenantId).stream()
            .map(VisitorResponse::from).toList();
    }
}
