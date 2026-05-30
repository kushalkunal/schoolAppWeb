package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.AdmitCard;
import in.schoolapp.academics.entity.AdmitCardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmitCardRepository extends JpaRepository<AdmitCard, UUID> {

    Optional<AdmitCard> findByExamIdAndStudentId(UUID examId, UUID studentId);

    List<AdmitCard> findByExamIdOrderByStudentId(UUID examId);

    List<AdmitCard> findByExamIdAndStatusOrderByStudentId(UUID examId, AdmitCardStatus status);

    long countByExamId(UUID examId);

    long countByExamIdAndStatus(UUID examId, AdmitCardStatus status);

    /** Find all BLOCKED cards for students who have since cleared their dues. */
    @Query("""
        SELECT ac FROM AdmitCard ac
        WHERE ac.examId = :examId
          AND ac.status = 'BLOCKED'
          AND ac.feeCleared = TRUE
        """)
    List<AdmitCard> findBlockedButClearedByExam(@Param("examId") UUID examId);

    /** Cards blocked for a specific student across all exams — used for auto-regeneration trigger. */
    @Query("SELECT ac FROM AdmitCard ac WHERE ac.studentId = :studentId AND ac.status = 'BLOCKED'")
    List<AdmitCard> findBlockedByStudent(@Param("studentId") UUID studentId);

    @Query("""
        SELECT COUNT(ac) FROM AdmitCard ac
        WHERE ac.schoolId = :schoolId
          AND ac.status IN ('GENERATED', 'DOWNLOADED')
        """)
    long countGeneratedBySchool(@Param("schoolId") UUID schoolId);
}
