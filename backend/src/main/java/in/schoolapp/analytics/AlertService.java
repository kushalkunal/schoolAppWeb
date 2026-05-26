package in.schoolapp.analytics;

import in.schoolapp.analytics.entity.Alert;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.analytics.repository.AlertRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Read + dismiss surface for {@link Alert} rows. Creation happens exclusively inside scheduled
 * detectors (consecutive-absence, attendance-not-submitted, fee-drop, at-risk) via
 * {@link #recordStudentAlert} / {@link #recordSectionAlert} — both enforce a one-per-day
 * idempotency check so re-running a cron doesn't spam duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public List<Alert> listActive(UUID tenantId) {
        return alertRepository.findBySchoolIdAndDismissedFalseOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Alert> listActiveBySeverity(UUID tenantId, List<AlertSeverity> severities) {
        if (severities == null || severities.isEmpty()) return listActive(tenantId);
        return alertRepository.findBySchoolIdAndDismissedFalseAndSeverityInOrderByCreatedAtDesc(
            tenantId, severities);
    }

    @Transactional
    public Alert dismiss(UUID tenantId, UUID alertId) {
        Alert a = alertRepository.findByIdAndSchoolId(alertId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.VALIDATION_ERROR, "Alert", alertId));
        if (a.isDismissed()) return a;
        a.setDismissed(true);
        a.setDismissedAt(OffsetDateTime.now());
        a.setDismissedById(TenantContext.getStaffId());
        return alertRepository.save(a);
    }

    /**
     * Insert an alert keyed on (tenant, type, student). Silently no-ops if an identical alert
     * was created earlier today — prevents duplicate rows when a detector is manually re-run.
     * Returns {@code null} if the write was suppressed.
     */
    @Transactional
    public Alert recordStudentAlert(UUID tenantId, AlertType type, AlertSeverity severity,
                                    UUID studentId, String title, String description,
                                    String actionUrl) {
        if (studentId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "studentId required");
        }
        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
            .atOffset(ZoneOffset.UTC);
        if (alertRepository.existsBySchoolIdAndAlertTypeAndStudentIdAndCreatedAtAfter(
                tenantId, type, studentId, startOfDay)) {
            log.debug("Suppressing duplicate alert type={} student={}", type, studentId);
            return null;
        }
        Alert a = new Alert();
        a.setSchoolId(tenantId);
        a.setAlertType(type);
        a.setSeverity(severity);
        a.setStudentId(studentId);
        a.setTitle(title);
        a.setDescription(description);
        a.setActionUrl(actionUrl);
        // 48h TTL on student-alerts so a dashboard left open doesn't stare at stale rows forever.
        a.setExpiresAt(OffsetDateTime.now().plusHours(48));
        return alertRepository.save(a);
    }

    @Transactional
    public Alert recordSectionAlert(UUID tenantId, AlertType type, AlertSeverity severity,
                                    UUID sectionId, String title, String description,
                                    String actionUrl) {
        if (sectionId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "sectionId required");
        }
        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
            .atOffset(ZoneOffset.UTC);
        if (alertRepository.existsBySchoolIdAndAlertTypeAndSectionIdAndCreatedAtAfter(
                tenantId, type, sectionId, startOfDay)) {
            return null;
        }
        Alert a = new Alert();
        a.setSchoolId(tenantId);
        a.setAlertType(type);
        a.setSeverity(severity);
        a.setSectionId(sectionId);
        a.setTitle(title);
        a.setDescription(description);
        a.setActionUrl(actionUrl);
        a.setExpiresAt(OffsetDateTime.now().plusHours(24));
        return alertRepository.save(a);
    }

    /**
     * Counts used by the daily digest — a lightweight (HIGH, CRITICAL) roll-up of the
     * dashboard's state. The scheduler calls this and formats into a WhatsApp template.
     */
    @Transactional(readOnly = true)
    public AlertCounts countsForDigest(UUID tenantId) {
        long total = alertRepository.countBySchoolIdAndDismissedFalse(tenantId);
        long high = alertRepository.countBySchoolIdAndDismissedFalseAndSeverity(tenantId, AlertSeverity.HIGH);
        long critical = alertRepository.countBySchoolIdAndDismissedFalseAndSeverity(tenantId, AlertSeverity.CRITICAL);
        return new AlertCounts(total, high, critical);
    }

    public record AlertCounts(long total, long high, long critical) {}
}
