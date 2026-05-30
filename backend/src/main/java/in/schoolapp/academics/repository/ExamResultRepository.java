package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamResult;
import in.schoolapp.academics.entity.ResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    Optional<ExamResult> findByExamIdAndStudentId(UUID examId, UUID studentId);

    List<ExamResult> findByExamIdAndSectionIdOrderByRankInSectionAsc(UUID examId, UUID sectionId);

    List<ExamResult> findByExamIdOrderByPercentageDesc(UUID examId);

    /** Toppers across all sections — rank 1 per section. */
    @Query("""
        SELECT r FROM ExamResult r
        WHERE r.examId = :examId AND r.rankInSection = 1
        ORDER BY r.percentage DESC
        """)
    List<ExamResult> findToppersByExam(@Param("examId") UUID examId);

    @Query("""
        SELECT COUNT(r) FROM ExamResult r
        WHERE r.examId = :examId AND r.sectionId = :sectionId AND r.pass = true
        """)
    long countPassByExamAndSection(@Param("examId") UUID examId, @Param("sectionId") UUID sectionId);

    @Query("""
        SELECT COUNT(r) FROM ExamResult r
        WHERE r.examId = :examId AND r.sectionId = :sectionId AND r.pass = false
        """)
    long countFailByExamAndSection(@Param("examId") UUID examId, @Param("sectionId") UUID sectionId);

    @Query("""
        SELECT r FROM ExamResult r
        WHERE r.examId = :examId AND r.sectionId = :sectionId AND r.status = :status
        """)
    List<ExamResult> findByExamIdAndSectionIdAndStatus(@Param("examId") UUID examId,
                                                       @Param("sectionId") UUID sectionId,
                                                       @Param("status") ResultStatus status);
}
