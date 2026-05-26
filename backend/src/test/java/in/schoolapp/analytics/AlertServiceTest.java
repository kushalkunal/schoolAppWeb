package in.schoolapp.analytics;

import in.schoolapp.analytics.entity.Alert;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.analytics.repository.AlertRepository;
import in.schoolapp.common.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;

    @InjectMocks AlertService service;

    @Test
    void recordStudentAlert_persistsWhenNoDuplicate() {
        UUID tenant = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(alertRepository.existsBySchoolIdAndAlertTypeAndStudentIdAndCreatedAtAfter(
            eq(tenant), eq(AlertType.CONSECUTIVE_ABSENCE), eq(studentId), any()))
            .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

        Alert result = service.recordStudentAlert(tenant, AlertType.CONSECUTIVE_ABSENCE,
            AlertSeverity.HIGH, studentId, "Absent 3+ days", "desc", "/x");

        assertThat(result).isNotNull();
        assertThat(result.getSchoolId()).isEqualTo(tenant);
        assertThat(result.getStudentId()).isEqualTo(studentId);
        assertThat(result.getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(result.getExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void recordStudentAlert_suppressesDuplicateSameDay() {
        UUID tenant = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(alertRepository.existsBySchoolIdAndAlertTypeAndStudentIdAndCreatedAtAfter(
            eq(tenant), eq(AlertType.CONSECUTIVE_ABSENCE), eq(studentId), any()))
            .thenReturn(true);

        Alert result = service.recordStudentAlert(tenant, AlertType.CONSECUTIVE_ABSENCE,
            AlertSeverity.HIGH, studentId, "dup", "desc", "/x");

        assertThat(result).isNull();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void recordStudentAlert_requiresStudentId() {
        assertThatThrownBy(() -> service.recordStudentAlert(
            UUID.randomUUID(), AlertType.OTHER, AlertSeverity.LOW, null, "t", "d", null))
            .isInstanceOf(AppException.class);
    }

    @Test
    void dismiss_marksDismissedAndSetsTimestamp() {
        UUID tenant = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        Alert a = new Alert();
        a.setId(alertId);
        a.setSchoolId(tenant);
        a.setAlertType(AlertType.OTHER);
        a.setDismissed(false);
        when(alertRepository.findByIdAndSchoolId(alertId, tenant)).thenReturn(Optional.of(a));
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

        Alert dismissed = service.dismiss(tenant, alertId);

        assertThat(dismissed.isDismissed()).isTrue();
        assertThat(dismissed.getDismissedAt()).isNotNull();
    }

    @Test
    void dismiss_isNoOpIfAlreadyDismissed() {
        UUID tenant = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        Alert a = new Alert();
        a.setId(alertId);
        a.setSchoolId(tenant);
        a.setDismissed(true);
        when(alertRepository.findByIdAndSchoolId(alertId, tenant)).thenReturn(Optional.of(a));

        service.dismiss(tenant, alertId);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void countsForDigest_rollsUpSeverities() {
        UUID tenant = UUID.randomUUID();
        when(alertRepository.countBySchoolIdAndDismissedFalse(tenant)).thenReturn(7L);
        when(alertRepository.countBySchoolIdAndDismissedFalseAndSeverity(tenant, AlertSeverity.HIGH)).thenReturn(3L);
        when(alertRepository.countBySchoolIdAndDismissedFalseAndSeverity(tenant, AlertSeverity.CRITICAL)).thenReturn(1L);

        AlertService.AlertCounts c = service.countsForDigest(tenant);

        assertThat(c.total()).isEqualTo(7);
        assertThat(c.high()).isEqualTo(3);
        assertThat(c.critical()).isEqualTo(1);
    }

    @Test
    void recordSectionAlert_persistsWithExpiry() {
        UUID tenant = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        when(alertRepository.existsBySchoolIdAndAlertTypeAndSectionIdAndCreatedAtAfter(
            eq(tenant), eq(AlertType.ATTENDANCE_NOT_SUBMITTED), eq(sectionId), any()))
            .thenReturn(false);
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        service.recordSectionAlert(tenant, AlertType.ATTENDANCE_NOT_SUBMITTED,
            AlertSeverity.MEDIUM, sectionId, "title", "desc", null);

        Alert saved = captor.getValue();
        assertThat(saved.getSectionId()).isEqualTo(sectionId);
        assertThat(saved.getExpiresAt()).isNotNull();
    }
}
