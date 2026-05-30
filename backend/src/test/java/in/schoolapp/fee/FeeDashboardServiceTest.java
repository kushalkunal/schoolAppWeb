package in.schoolapp.fee;

import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Collection register aggregation (audit #21). */
@ExtendWith(MockitoExtension.class)
class FeeDashboardServiceTest {

    @Mock FeeInvoiceRepository invoiceRepository;
    @Mock FeePaymentRepository paymentRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock SectionRepository sectionRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @InjectMocks FeeDashboardService service;

    @Test
    void collectionRegisterBreaksDownByModeWithTotals() {
        UUID tenant = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 6, 1);

        FeePaymentRepository.ModeTotalRow cash = mock(FeePaymentRepository.ModeTotalRow.class);
        when(cash.getMode()).thenReturn("CASH");
        when(cash.getAmountPaise()).thenReturn(50_000L);
        when(cash.getPaymentCount()).thenReturn(3L);
        when(paymentRepository.sumByModeBetween(tenant, day, day)).thenReturn(List.of(cash));
        when(paymentRepository.sumCollectedBetween(tenant, day, day)).thenReturn(50_000L);
        when(paymentRepository.countCollectedBetween(tenant, day, day)).thenReturn(3L);

        var resp = service.collectionRegister(tenant, day, day);

        assertThat(resp.totalPaise()).isEqualTo(50_000L);
        assertThat(resp.paymentCount()).isEqualTo(3L);
        assertThat(resp.byMode()).singleElement()
            .satisfies(row -> {
                assertThat(row.mode()).isEqualTo("CASH");
                assertThat(row.amountPaise()).isEqualTo(50_000L);
                assertThat(row.paymentCount()).isEqualTo(3L);
            });
    }
}
