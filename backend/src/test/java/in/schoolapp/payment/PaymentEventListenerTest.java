package in.schoolapp.payment;

import in.schoolapp.fee.FeePaymentService;
import in.schoolapp.payment.dto.PaymentEvent;
import in.schoolapp.payment.dto.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock FeePaymentService feePaymentService;

    @InjectMocks PaymentEventListener listener;

    @Test
    void paidEvent_createsOnlinePayment() {
        UUID tenant = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        PaymentEvent evt = new PaymentEvent(
            "cs_test_abc", PaymentStatus.PAID, 450000L, "upi",
            Map.of("tenant_id", tenant.toString(), "student_id", student.toString()));

        listener.onPaymentEvent(evt);

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> studentCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(feePaymentService).createOnlinePayment(
            tenantCaptor.capture(), studentCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(450000L),
            org.mockito.ArgumentMatchers.eq("cs_test_abc"),
            org.mockito.ArgumentMatchers.eq("upi"));
        assertThat(tenantCaptor.getValue()).isEqualTo(tenant);
        assertThat(studentCaptor.getValue()).isEqualTo(student);
    }

    @Test
    void nonPaidEvent_isIgnored() {
        PaymentEvent failed = new PaymentEvent(
            "cs_test_bad", PaymentStatus.FAILED, 450000L, null,
            Map.of("tenant_id", UUID.randomUUID().toString(),
                   "student_id", UUID.randomUUID().toString()));

        listener.onPaymentEvent(failed);

        verify(feePaymentService, never()).createOnlinePayment(any(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void missingMetadata_isDroppedQuietly() {
        PaymentEvent evt = new PaymentEvent(
            "cs_test_nomd", PaymentStatus.PAID, 100L, "card", Map.of());

        listener.onPaymentEvent(evt);

        verify(feePaymentService, never()).createOnlinePayment(any(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void nonPositiveAmount_isDropped() {
        PaymentEvent evt = new PaymentEvent(
            "cs_test_zero", PaymentStatus.PAID, 0L, "card",
            Map.of("tenant_id", UUID.randomUUID().toString(),
                   "student_id", UUID.randomUUID().toString()));

        listener.onPaymentEvent(evt);

        verify(feePaymentService, never()).createOnlinePayment(any(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void serviceException_isSwallowed() {
        UUID tenant = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        PaymentEvent evt = new PaymentEvent(
            "cs_test_boom", PaymentStatus.PAID, 100L, "card",
            Map.of("tenant_id", tenant.toString(), "student_id", student.toString()));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
            .when(feePaymentService).createOnlinePayment(any(), any(), anyLong(), anyString(), anyString());

        // Must not throw — webhook already acked to BSP.
        listener.onPaymentEvent(evt);
    }
}
