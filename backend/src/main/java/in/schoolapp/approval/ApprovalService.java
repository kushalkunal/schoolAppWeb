package in.schoolapp.approval;

import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalStatus;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.approval.repository.ApprovalRequestRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic maker-checker engine. A request is staged with {@link #submit}; the proposed change is
 * applied to the domain only when a <em>different</em> user calls {@link #approve} (segregation of
 * duties). Every transition is written to the audit log.
 * <p>
 * Apply-on-approve runs the matching {@link ApprovalHandler} inside the approval transaction, so the
 * domain mutation and the status flip commit atomically — a half-approved state is impossible.
 */
@Slf4j
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repo;
    private final AuditLogger audit;
    private final Map<ApprovalType, ApprovalHandler> handlers;

    public ApprovalService(ApprovalRequestRepository repo, AuditLogger audit,
                           List<ApprovalHandler> handlerBeans) {
        this.repo = repo;
        this.audit = audit;
        this.handlers = new HashMap<>();
        for (ApprovalHandler h : handlerBeans) {
            this.handlers.put(h.type(), h);
        }
    }

    @Transactional
    public ApprovalRequest submit(UUID tenantId, ApprovalType type, UUID subjectId,
                                  String payloadJson, long amountPaise, String summary) {
        ApprovalRequest r = new ApprovalRequest();
        r.setSchoolId(tenantId);
        r.setType(type);
        r.setStatus(ApprovalStatus.PENDING);
        r.setSubjectId(subjectId);
        r.setPayloadJson(payloadJson);
        r.setAmountPaise(amountPaise);
        r.setSummary(summary);
        r.setRequestedById(TenantContext.getStaffId());
        r.setRequestedByRole(TenantContext.getRole());
        r = repo.save(r);
        audit.logAction(tenantId, "ApprovalRequest", r.getId(), "APPROVAL_REQUESTED",
            Map.of("type", type.name(), "amountPaise", amountPaise,
                "summary", summary == null ? "" : summary));
        return r;
    }

    @Transactional
    public ApprovalRequest approve(UUID tenantId, UUID id, String note) {
        ApprovalRequest r = loadPending(tenantId, id);

        UUID approver = TenantContext.getStaffId();
        if (approver != null && approver.equals(r.getRequestedById())) {
            throw new AppException(ErrorCode.APPROVAL_SELF_NOT_ALLOWED,
                "You cannot approve your own request");
        }

        handler(r.getType()).apply(r);   // staged change takes effect here, in this transaction

        r.setStatus(ApprovalStatus.APPROVED);
        r.setDecidedById(approver);
        r.setDecidedAt(OffsetDateTime.now());
        r.setDecisionNote(note);
        repo.save(r);
        audit.logAction(tenantId, "ApprovalRequest", r.getId(), "APPROVAL_APPROVED",
            Map.of("type", r.getType().name(), "subjectId", String.valueOf(r.getSubjectId())));
        log.info("Approval approved tenant={} id={} type={} approver={}",
            tenantId, id, r.getType(), approver);
        return r;
    }

    @Transactional
    public ApprovalRequest reject(UUID tenantId, UUID id, String note) {
        ApprovalRequest r = loadPending(tenantId, id);
        r.setStatus(ApprovalStatus.REJECTED);
        r.setDecidedById(TenantContext.getStaffId());
        r.setDecidedAt(OffsetDateTime.now());
        r.setDecisionNote(note);
        repo.save(r);
        audit.logAction(tenantId, "ApprovalRequest", r.getId(), "APPROVAL_REJECTED",
            Map.of("type", r.getType().name()));
        return r;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> list(UUID tenantId, ApprovalStatus status) {
        return status != null
            ? repo.findBySchoolIdAndStatusOrderByCreatedAtDesc(tenantId, status)
            : repo.findBySchoolIdOrderByCreatedAtDesc(tenantId);
    }

    private ApprovalRequest loadPending(UUID tenantId, UUID id) {
        ApprovalRequest r = repo.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.APPROVAL_NOT_FOUND, "Approval request not found"));
        if (r.getStatus() != ApprovalStatus.PENDING) {
            throw new AppException(ErrorCode.APPROVAL_NOT_PENDING,
                "Approval request is already " + r.getStatus());
        }
        return r;
    }

    private ApprovalHandler handler(ApprovalType type) {
        ApprovalHandler h = handlers.get(type);
        if (h == null) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "No approval handler for " + type);
        }
        return h;
    }
}
