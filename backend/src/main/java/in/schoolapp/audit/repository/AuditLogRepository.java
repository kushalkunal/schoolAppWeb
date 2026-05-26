package in.schoolapp.audit.repository;

import in.schoolapp.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Per-entity history — used by an "activity" tab on student/payment/etc. detail screens. */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    /** Per-tenant audit stream — paginated for a compliance / admin review screen. */
    Page<AuditLog> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId, Pageable pageable);
}
