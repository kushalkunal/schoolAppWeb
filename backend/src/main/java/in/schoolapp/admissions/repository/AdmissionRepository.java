package in.schoolapp.admissions.repository;

import in.schoolapp.admissions.entity.Admission;
import in.schoolapp.admissions.entity.AdmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository extends JpaRepository<Admission, UUID> {

    Optional<Admission> findByIdAndSchoolId(UUID id, UUID schoolId);

    Page<Admission> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId, Pageable pageable);

    Page<Admission> findBySchoolIdAndStatusOrderByCreatedAtDesc(
        UUID schoolId, AdmissionStatus status, Pageable pageable);

    long countBySchoolIdAndStatus(UUID schoolId, AdmissionStatus status);

    /** Duplicate detection (#14): a still-live application for the same child + contact. */
    @Query("""
        SELECT COUNT(a) FROM Admission a
        WHERE a.schoolId = :schoolId
          AND a.parentPhone = :parentPhone
          AND LOWER(a.studentFirstName) = LOWER(:firstName)
          AND a.status NOT IN :excluded
        """)
    long countActiveDuplicates(@Param("schoolId") UUID schoolId,
                               @Param("parentPhone") String parentPhone,
                               @Param("firstName") String firstName,
                               @Param("excluded") List<AdmissionStatus> excluded);
}
