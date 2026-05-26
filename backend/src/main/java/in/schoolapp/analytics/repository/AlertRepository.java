package in.schoolapp.analytics.repository;

import in.schoolapp.analytics.entity.Alert;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findBySchoolIdAndDismissedFalseOrderByCreatedAtDesc(UUID schoolId);

    List<Alert> findBySchoolIdAndDismissedFalseAndSeverityInOrderByCreatedAtDesc(
        UUID schoolId, List<AlertSeverity> severities);

    /**
     * Idempotency guard for detectors — a daily cron should not create a duplicate alert of
     * the same type for the same student when re-run. Callers typically check this before
     * inserting a new row.
     */
    boolean existsBySchoolIdAndAlertTypeAndStudentIdAndCreatedAtAfter(
        UUID schoolId, AlertType alertType, UUID studentId, OffsetDateTime createdAtAfter);

    boolean existsBySchoolIdAndAlertTypeAndSectionIdAndCreatedAtAfter(
        UUID schoolId, AlertType alertType, UUID sectionId, OffsetDateTime createdAtAfter);

    long countBySchoolIdAndDismissedFalse(UUID schoolId);

    long countBySchoolIdAndDismissedFalseAndSeverity(UUID schoolId, AlertSeverity severity);

    /** Find a specific alert scoped to tenant — for the dismiss endpoint's tenant-boundary check. */
    java.util.Optional<Alert> findByIdAndSchoolId(UUID id, UUID schoolId);
}
