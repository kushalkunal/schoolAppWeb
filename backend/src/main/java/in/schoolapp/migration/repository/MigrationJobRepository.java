package in.schoolapp.migration.repository;

import in.schoolapp.migration.entity.MigrationJob;
import in.schoolapp.migration.entity.MigrationJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationJobRepository extends JpaRepository<MigrationJob, UUID> {

    Optional<MigrationJob> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<MigrationJob> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);

    List<MigrationJob> findBySchoolIdAndStatusOrderByCreatedAtDesc(
        UUID schoolId, MigrationJobStatus status);
}
