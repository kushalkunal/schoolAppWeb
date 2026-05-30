package in.schoolapp.cashrecon;

import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.cashrecon.dto.CloseDrawerRequest;
import in.schoolapp.cashrecon.entity.CashReconciliation;
import in.schoolapp.approval.ApprovalService;
import in.schoolapp.fee.repository.FeePaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers cash-variance escalation (audit #9): an over/short above the threshold raises a
 * maker-checker approval and leaves the record unreviewed; a balanced drawer does not.
 */
@ExtendWith(MockitoExtension.class)
class CashReconciliationServiceTest {

    @Mock CashReconciliationRepository repo;
    @Mock FeePaymentRepository paymentRepo;
    @Mock ApprovalService approvalService;
    CashReconciliationService service;

    final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CashReconciliationService(repo, paymentRepo, approvalService);
        ReflectionTestUtils.setField(service, "varianceThresholdPaise", 0L);
        when(paymentRepo.sumByModeBetween(eq(tenant), any(), any())).thenReturn(List.of());  // expected = 0
        when(repo.save(any(CashReconciliation.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void varianceAboveThresholdEscalatesAndStaysUnreviewed() {
        // Counted ₹500 cash but nothing expected -> +50000 paise over.
        var resp = service.closeDrawer(tenant, new CloseDrawerRequest(LocalDate.of(2026, 6, 1), 50_000, 0, 0, 0, null));

        assertThat(resp.variancePaise()).isEqualTo(50_000);
        assertThat(resp.varianceReviewed()).isFalse();
        verify(approvalService).submit(eq(tenant), eq(ApprovalType.CASH_VARIANCE), any(), any(), anyLong(), any());
    }

    @Test
    void balancedDrawerDoesNotEscalate() {
        var resp = service.closeDrawer(tenant, new CloseDrawerRequest(LocalDate.of(2026, 6, 1), 0, 0, 0, 0, null));

        assertThat(resp.variancePaise()).isZero();
        assertThat(resp.varianceReviewed()).isTrue();
        verify(approvalService, never()).submit(any(), any(), any(), any(), anyLong(), any());
    }
}
