package in.schoolapp.analytics.detector;

import in.schoolapp.academics.entity.ReportCard;
import in.schoolapp.academics.repository.ReportCardRepository;
import in.schoolapp.analytics.AlertService;
import in.schoolapp.analytics.entity.MarksTrend;
import in.schoolapp.analytics.entity.RiskFactor;
import in.schoolapp.analytics.repository.StudentRiskScoreRepository;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.fee.FeeInvoiceService;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtRiskDetectionServiceTest {

    @Mock SchoolRepository schoolRepository;
    @Mock StudentRepository studentRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock ReportCardRepository reportCardRepository;
    @Mock FeeInvoiceService feeInvoiceService;
    @Mock StudentRiskScoreRepository riskScoreRepository;
    @Mock AlertService alertService;
    // Slice 19b: LLM injected for narrative writing — tests stub it to return null so the
    // fallback to the deterministic describe() template kicks in.
    @Mock in.schoolapp.ai.llm.LlmClient llmClient;

    @InjectMocks AtRiskDetectionService service;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(service, "windowDays", 30);
        ReflectionTestUtils.setField(service, "feeBenchmarkPaise", 5_000_000L);
        ReflectionTestUtils.setField(service, "alertThreshold", 70);
    }

    @Test
    void attendanceDrivenRisk_flagsAttendanceAsTopFactor() {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setSchoolId(UUID.randomUUID());
        s.setFirstName("A");
        s.setActive(true);

        // 10 absences out of 20 recorded days → 50% attendance → sub-score 50.
        AttendanceRepository.StudentAttendanceCountRow row = countRow(20, 10);
        when(feeInvoiceService.getOutstanding(s.getId())).thenReturn(0L);
        when(reportCardRepository.findByStudentIdOrderByCreatedAtDesc(s.getId())).thenReturn(List.of());

        AtRiskDetectionService.Score score = service.computeScore(s, row);

        assertThat(score.total()).isEqualTo(50);
        assertThat(score.topFactor()).isEqualTo(RiskFactor.ATTENDANCE);
        assertThat(score.attendancePct()).isEqualTo(new BigDecimal("50.00"));
        assertThat(score.marksTrend()).isEqualTo(MarksTrend.UNKNOWN);
    }

    @Test
    void feeDrivenRisk_scoresAtBenchmark() {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setFirstName("B");
        AttendanceRepository.StudentAttendanceCountRow att = countRow(20, 0);  // 100% attendance → 0
        when(feeInvoiceService.getOutstanding(s.getId())).thenReturn(5_000_000L);  // exactly benchmark
        when(reportCardRepository.findByStudentIdOrderByCreatedAtDesc(s.getId())).thenReturn(List.of());

        AtRiskDetectionService.Score score = service.computeScore(s, att);

        assertThat(score.total()).isEqualTo(100);
        assertThat(score.topFactor()).isEqualTo(RiskFactor.FEE);
        assertThat(score.feeOutstandingPaise()).isEqualTo(5_000_000L);
    }

    @Test
    void marksDrop_flagsMarksWhenOthersAreZero() {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setFirstName("C");
        AttendanceRepository.StudentAttendanceCountRow att = countRow(20, 0);
        when(feeInvoiceService.getOutstanding(s.getId())).thenReturn(0L);

        ReportCard latest = new ReportCard();
        latest.setPercentage(new BigDecimal("45.00"));
        ReportCard prior = new ReportCard();
        prior.setPercentage(new BigDecimal("65.00"));
        when(reportCardRepository.findByStudentIdOrderByCreatedAtDesc(s.getId()))
            .thenReturn(List.of(latest, prior));

        AtRiskDetectionService.Score score = service.computeScore(s, att);

        assertThat(score.marksTrend()).isEqualTo(MarksTrend.DOWN);
        assertThat(score.total()).isEqualTo(40);
        assertThat(score.topFactor()).isEqualTo(RiskFactor.MARKS);
    }

    @Test
    void noAttendanceData_skipsAttendanceSubScore() {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setFirstName("D");
        when(feeInvoiceService.getOutstanding(s.getId())).thenReturn(0L);
        when(reportCardRepository.findByStudentIdOrderByCreatedAtDesc(s.getId())).thenReturn(List.of());

        AtRiskDetectionService.Score score = service.computeScore(s, null);

        assertThat(score.attendancePct()).isNull();
        // Fee=0, marks trend UNKNOWN → marksSub=30, total=30
        assertThat(score.total()).isEqualTo(30);
    }

    @Test
    void marksWithinPercentThreshold_treatedAsFlat() {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        AttendanceRepository.StudentAttendanceCountRow att = countRow(20, 0);
        when(feeInvoiceService.getOutstanding(s.getId())).thenReturn(0L);

        ReportCard latest = new ReportCard();
        latest.setPercentage(new BigDecimal("70.00"));
        ReportCard prior = new ReportCard();
        prior.setPercentage(new BigDecimal("72.00"));  // -2%, within threshold
        when(reportCardRepository.findByStudentIdOrderByCreatedAtDesc(s.getId()))
            .thenReturn(List.of(latest, prior));

        AtRiskDetectionService.Score score = service.computeScore(s, att);

        assertThat(score.marksTrend()).isEqualTo(MarksTrend.FLAT);
        assertThat(score.total()).isZero();
    }

    private static AttendanceRepository.StudentAttendanceCountRow countRow(long total, long absent) {
        return new AttendanceRepository.StudentAttendanceCountRow() {
            @Override public UUID getStudentId() { return UUID.randomUUID(); }
            @Override public long getTotalCount() { return total; }
            @Override public long getAbsentCount() { return absent; }
        };
    }
}
