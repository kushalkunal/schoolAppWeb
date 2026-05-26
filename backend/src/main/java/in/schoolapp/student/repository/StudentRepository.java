package in.schoolapp.student.repository;

import in.schoolapp.student.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByIdAndSchoolId(UUID id, UUID schoolId);

    Page<Student> findBySchoolIdAndActiveTrue(UUID schoolId, Pageable pageable);

    boolean existsBySchoolIdAndAdmissionNumber(UUID schoolId, String admissionNumber);

    /**
     * Fuzzy name search using the {@code idx_students_name_trgm} GIN index from V1 — matches
     * substrings in first_name + last_name and the admission number prefix. Used by the student
     * picker in the quick-collect flow (gap analysis §5.3) and by OCR entity matching.
     */
    @Query(value = """
        SELECT * FROM students
        WHERE school_id = :schoolId
          AND is_active = TRUE
          AND (
            (first_name || ' ' || COALESCE(last_name, '')) ILIKE '%' || :q || '%'
            OR admission_number ILIKE :q || '%'
          )
        ORDER BY first_name
        """, nativeQuery = true)
    Page<Student> searchBySchoolId(@Param("schoolId") UUID schoolId,
                                   @Param("q") String query,
                                   Pageable pageable);

    /** Delta pull for mobile sync (Slice 11). Ordered by updatedAt so the client's high-water
     *  mark advances monotonically. */
    List<Student> findBySchoolIdAndUpdatedAtAfterOrderByUpdatedAtAsc(
        UUID schoolId, OffsetDateTime updatedAtAfter);

    List<Student> findBySchoolIdOrderByUpdatedAtAsc(UUID schoolId);
}
