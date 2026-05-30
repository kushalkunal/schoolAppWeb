package in.schoolapp.approval;

import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalStatus;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.approval.repository.ApprovalRequestRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the engine's segregation-of-duties invariants: a maker cannot be the checker, the staged
 * change is applied only on approval (and not on reject / self-approval), and decided requests are
 * terminal.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock ApprovalRequestRepository repo;
    @Mock AuditLogger audit;

    ApprovalService service;
    RecordingHandler handler;

    final UUID tenant = UUID.randomUUID();
    final UUID maker = UUID.randomUUID();
    final UUID checker = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        service = new ApprovalService(repo, audit, List.of(handler));
        // lenient: tests that throw before saving (self-approve, non-pending) never hit save().
        lenient().when(repo.save(any(ApprovalRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void submitCreatesPendingRequestOwnedByRequester() {
        TenantContext.set(tenant, maker, "ADMIN");
        ApprovalRequest r = service.submit(
            tenant, ApprovalType.FEE_DISCOUNT, UUID.randomUUID(), null, 5_000, "10% scholarship");
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(r.getRequestedById()).isEqualTo(maker);
    }

    @Test
    void approveAppliesStagedChangeAndMarksApproved() {
        ApprovalRequest pending = pendingRequestedBy(maker);
        when(repo.findByIdAndSchoolId(pending.getId(), tenant)).thenReturn(Optional.of(pending));
        TenantContext.set(tenant, checker, "PRINCIPAL");

        ApprovalRequest r = service.approve(tenant, pending.getId(), "looks good");

        assertThat(handler.applied).isTrue();
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(r.getDecidedById()).isEqualTo(checker);
    }

    @Test
    void makerCannotApproveOwnRequest_andChangeIsNotApplied() {
        ApprovalRequest pending = pendingRequestedBy(maker);
        when(repo.findByIdAndSchoolId(pending.getId(), tenant)).thenReturn(Optional.of(pending));
        TenantContext.set(tenant, maker, "ADMIN");   // same person who requested

        assertThatThrownBy(() -> service.approve(tenant, pending.getId(), null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_SELF_NOT_ALLOWED);

        assertThat(handler.applied).isFalse();
        assertThat(pending.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void cannotDecideAnAlreadyDecidedRequest() {
        ApprovalRequest done = pendingRequestedBy(maker);
        done.setStatus(ApprovalStatus.APPROVED);
        when(repo.findByIdAndSchoolId(done.getId(), tenant)).thenReturn(Optional.of(done));
        TenantContext.set(tenant, checker, "PRINCIPAL");

        assertThatThrownBy(() -> service.approve(tenant, done.getId(), null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_NOT_PENDING);
    }

    @Test
    void rejectMarksRejectedWithoutApplyingTheChange() {
        ApprovalRequest pending = pendingRequestedBy(maker);
        when(repo.findByIdAndSchoolId(pending.getId(), tenant)).thenReturn(Optional.of(pending));
        TenantContext.set(tenant, checker, "PRINCIPAL");

        ApprovalRequest r = service.reject(tenant, pending.getId(), "not justified");

        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(handler.applied).isFalse();
    }

    private ApprovalRequest pendingRequestedBy(UUID requester) {
        ApprovalRequest r = new ApprovalRequest();
        r.setId(UUID.randomUUID());
        r.setSchoolId(tenant);
        r.setType(ApprovalType.FEE_DISCOUNT);
        r.setStatus(ApprovalStatus.PENDING);
        r.setSubjectId(UUID.randomUUID());
        r.setRequestedById(requester);
        return r;
    }

    /** Stand-in handler that records whether the staged change was applied. */
    static class RecordingHandler implements ApprovalHandler {
        boolean applied = false;

        @Override public ApprovalType type() { return ApprovalType.FEE_DISCOUNT; }

        @Override public void apply(ApprovalRequest request) { applied = true; }
    }
}
