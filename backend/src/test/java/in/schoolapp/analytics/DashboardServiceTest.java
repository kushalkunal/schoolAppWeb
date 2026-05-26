package in.schoolapp.analytics;

import in.schoolapp.analytics.dto.DashboardResponse;
import in.schoolapp.analytics.entity.MarksTrend;
import in.schoolapp.analytics.entity.RiskFactor;
import in.schoolapp.analytics.entity.StudentRiskScore;
import in.schoolapp.analytics.repository.StudentRiskScoreRepository;
import in.schoolapp.attendance.AttendanceAnalyticsService;
import in.schoolapp.attendance.dto.AttendanceSummaryResponse;
import in.schoolapp.attendance.dto.UnmarkedSectionResponse;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock AttendanceAnalyticsService attendanceAnalyticsService;
    @Mock AlertService alertService;
    @Mock FeePaymentRepository feePaymentRepository;
    @Mock StudentRiskScoreRepository riskScoreRepository;
    @Mock StudentRepository studentRepository;

    @InjectMocks DashboardService service;

    @Test
    void build_composesAllTiles() {
        UUID tenant = UUID.randomUUID();
        UUID student1 = UUID.randomUUID();

        LocalDate today = LocalDate.now();
        when(attendanceAnalyticsService.schoolSummary(eq(tenant), any()))
            .thenReturn(new AttendanceSummaryResponse(today, 100, 90, 5, 3, 1, 1));
        when(alertService.countsForDigest(tenant))
            .thenReturn(new AlertService.AlertCounts(7, 3, 1));
        when(feePaymentRepository.sumCollectedBetween(eq(tenant), any(), any()))
            .thenReturn(125_000_00L);  // ₹125,000 in paise
        when(riskScoreRepository.countBySchoolIdAndScoreGreaterThanEqual(eq(tenant), anyInt()))
            .thenReturn(4L);
        when(attendanceAnalyticsService.unmarkedSections(eq(tenant), any()))
            .thenReturn(List.of(
                new UnmarkedSectionResponse(UUID.randomUUID(), "A", "5", UUID.randomUUID(), "Ms X", today),
                new UnmarkedSectionResponse(UUID.randomUUID(), "B", "5", UUID.randomUUID(), "Mr Y", today)));

        StudentRiskScore top1 = new StudentRiskScore();
        top1.setStudentId(student1);
        top1.setScore(88);
        top1.setTopFactor(RiskFactor.FEE);
        top1.setMarksTrend(MarksTrend.DOWN);
        when(riskScoreRepository.findBySchoolIdOrderByScoreDesc(eq(tenant), any(Pageable.class)))
            .thenReturn(List.of(top1));

        Student s = new Student();
        s.setId(student1);
        s.setFirstName("Risky");
        when(studentRepository.findAllById(List.of(student1))).thenReturn(List.of(s));

        DashboardResponse resp = service.build(tenant);

        assertThat(resp.attendance().totalMarked()).isEqualTo(100);
        assertThat(resp.alerts().high()).isEqualTo(3);
        assertThat(resp.alerts().critical()).isEqualTo(1);
        assertThat(resp.fees().mtdCollectedPaise()).isEqualTo(125_000_00L);
        assertThat(resp.fees().activeAtRiskCount()).isEqualTo(4L);
        assertThat(resp.unmarkedSectionsCount()).isEqualTo(2);
        assertThat(resp.topAtRisk()).hasSize(1);
        assertThat(resp.topAtRisk().get(0).studentName()).isEqualTo("Risky");
        assertThat(resp.topAtRisk().get(0).score()).isEqualTo(88);
        assertThat(resp.topAtRisk().get(0).topFactor()).isEqualTo("FEE");
    }

    @Test
    void build_returnsEmptyAtRiskList_whenNoScoresYet() {
        UUID tenant = UUID.randomUUID();
        when(attendanceAnalyticsService.schoolSummary(eq(tenant), any()))
            .thenReturn(new AttendanceSummaryResponse(LocalDate.now(), 0, 0, 0, 0, 0, 0));
        when(alertService.countsForDigest(tenant))
            .thenReturn(new AlertService.AlertCounts(0, 0, 0));
        when(feePaymentRepository.sumCollectedBetween(eq(tenant), any(), any())).thenReturn(0L);
        when(riskScoreRepository.countBySchoolIdAndScoreGreaterThanEqual(eq(tenant), anyInt()))
            .thenReturn(0L);
        when(attendanceAnalyticsService.unmarkedSections(eq(tenant), any())).thenReturn(List.of());
        when(riskScoreRepository.findBySchoolIdOrderByScoreDesc(eq(tenant), any(Pageable.class)))
            .thenReturn(List.of());

        DashboardResponse resp = service.build(tenant);

        assertThat(resp.topAtRisk()).isEmpty();
        assertThat(resp.unmarkedSectionsCount()).isZero();
    }
}
