package in.schoolapp.fee.structure.repository;

import in.schoolapp.fee.structure.entity.FeeStructureVersion;
import in.schoolapp.fee.structure.entity.FeeStructureVersion.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeStructureVersionRepository extends JpaRepository<FeeStructureVersion, UUID> {

    Optional<FeeStructureVersion> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<FeeStructureVersion> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);

    Optional<FeeStructureVersion> findFirstBySchoolIdAndAcademicYearIdAndStatus(
        UUID schoolId, UUID academicYearId, Status status);
}
