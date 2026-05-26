package in.schoolapp.fee.structure;

import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.structure.entity.FeeStructureRow;
import in.schoolapp.fee.structure.entity.FeeStructureTerm;
import in.schoolapp.fee.structure.entity.FeeStructureVersion;
import in.schoolapp.fee.structure.entity.FeeStructureVersion.Status;
import in.schoolapp.fee.structure.repository.FeeStructureRowRepository;
import in.schoolapp.fee.structure.repository.FeeStructureTermRepository;
import in.schoolapp.fee.structure.repository.FeeStructureVersionRepository;
import in.schoolapp.student.event.StudentEnrolledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MidYearAutoInvoiceListenerTest {

    @Mock FeeStructureVersionRepository versionRepo;
    @Mock FeeStructureTermRepository termRepo;
    @Mock FeeStructureRowRepository rowRepo;
    @Mock FeeInvoiceRepository invoiceRepo;

    @InjectMocks MidYearAutoInvoiceListener listener;

    private final UUID tenant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID section = UUID.randomUUID();
    private final UUID year = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID headId = UUID.randomUUID();

    @Test
    void noActiveVersion_doesNothing() {
        when(versionRepo.findFirstBySchoolIdAndAcademicYearIdAndStatus(tenant, year, Status.ACTIVE))
            .thenReturn(Optional.empty());

        listener.onStudentEnrolled(new StudentEnrolledEvent(tenant, student, section, year, classId));

        verify(invoiceRepo, never()).saveAndFlush(any());
    }

    @Test
    void annualRow_alwaysIssued() {
        givenActiveVersion();
        when(rowRepo.findByVersionIdAndClassId(versionId, classId))
            .thenReturn(List.of(row(null, 500_000L)));   // ₹5000 annual
        when(termRepo.findByVersionIdOrderByTermNumberAsc(versionId)).thenReturn(List.of());

        listener.onStudentEnrolled(new StudentEnrolledEvent(tenant, student, section, year, classId));

        ArgumentCaptor<FeeInvoice> cap = ArgumentCaptor.forClass(FeeInvoice.class);
        verify(invoiceRepo, times(1)).saveAndFlush(cap.capture());
        FeeInvoice inv = cap.getValue();
        assertThat(inv.getAmountDuePaise()).isEqualTo(500_000L);
        assertThat(inv.getStructureTermNumber()).isNull();
        assertThat(inv.getDueDate()).isAfter(LocalDate.now().minusDays(1));
    }

    @Test
    void pastTerm_isSkipped_futureTerm_isIssued() {
        givenActiveVersion();
        when(rowRepo.findByVersionIdAndClassId(versionId, classId)).thenReturn(List.of(
            row(1, 200_000L),
            row(2, 300_000L)
        ));
        // Term 1 already ended a month ago; Term 2 ends in 30 days.
        FeeStructureTerm t1 = term(1, LocalDate.now().minusDays(90), LocalDate.now().minusDays(30),
            LocalDate.now().minusDays(40));
        FeeStructureTerm t2 = term(2, LocalDate.now().minusDays(10), LocalDate.now().plusDays(30),
            LocalDate.now().plusDays(15));
        when(termRepo.findByVersionIdOrderByTermNumberAsc(versionId)).thenReturn(List.of(t1, t2));

        listener.onStudentEnrolled(new StudentEnrolledEvent(tenant, student, section, year, classId));

        ArgumentCaptor<FeeInvoice> cap = ArgumentCaptor.forClass(FeeInvoice.class);
        verify(invoiceRepo, times(1)).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getStructureTermNumber()).isEqualTo(2);
        assertThat(cap.getValue().getAmountDuePaise()).isEqualTo(300_000L);
    }

    @Test
    void optionalRow_isSkipped() {
        givenActiveVersion();
        FeeStructureRow optional = row(null, 100_000L);
        optional.setOptional(true);
        when(rowRepo.findByVersionIdAndClassId(versionId, classId)).thenReturn(List.of(optional));
        when(termRepo.findByVersionIdOrderByTermNumberAsc(versionId)).thenReturn(List.of());

        listener.onStudentEnrolled(new StudentEnrolledEvent(tenant, student, section, year, classId));

        verify(invoiceRepo, never()).saveAndFlush(any());
    }

    @Test
    void zeroAmountRow_isSkipped() {
        givenActiveVersion();
        when(rowRepo.findByVersionIdAndClassId(versionId, classId))
            .thenReturn(List.of(row(null, 0L)));
        when(termRepo.findByVersionIdOrderByTermNumberAsc(versionId)).thenReturn(List.of());

        listener.onStudentEnrolled(new StudentEnrolledEvent(tenant, student, section, year, classId));

        verify(invoiceRepo, never()).saveAndFlush(any());
    }

    // ---------- helpers ----------

    private void givenActiveVersion() {
        FeeStructureVersion v = new FeeStructureVersion();
        v.setId(versionId);
        v.setSchoolId(tenant);
        v.setAcademicYearId(year);
        v.setStatus(Status.ACTIVE);
        v.setName("2026 main");
        when(versionRepo.findFirstBySchoolIdAndAcademicYearIdAndStatus(tenant, year, Status.ACTIVE))
            .thenReturn(Optional.of(v));
    }

    private FeeStructureRow row(Integer termNumber, long amountPaise) {
        FeeStructureRow r = new FeeStructureRow();
        r.setVersionId(versionId);
        r.setClassId(classId);
        r.setFeeHeadId(headId);
        r.setTermNumber(termNumber);
        r.setAmountPaise(amountPaise);
        r.setOptional(false);
        return r;
    }

    private FeeStructureTerm term(int num, LocalDate start, LocalDate end, LocalDate due) {
        FeeStructureTerm t = new FeeStructureTerm();
        t.setVersionId(versionId);
        t.setTermNumber(num);
        t.setName("Term " + num);
        t.setStartDate(start);
        t.setEndDate(end);
        t.setDueDate(due);
        return t;
    }
}
