package in.schoolapp.admissions.repository;

import in.schoolapp.admissions.entity.Admission;
import in.schoolapp.admissions.entity.AdmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository extends JpaRepository<Admission, UUID> {

    Optional<Admission> findByIdAndSchoolId(UUID id, UUID schoolId);

    Page<Admission> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId, Pageable pageable);

    Page<Admission> findBySchoolIdAndStatusOrderByCreatedAtDesc(
        UUID schoolId, AdmissionStatus status, Pageable pageable);

    long countBySchoolIdAndStatus(UUID schoolId, AdmissionStatus status);
}
