package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamMarkRepository extends JpaRepository<ExamMark, UUID> {

    Optional<ExamMark> findByExamIdAndStudentIdAndSubjectId(
        UUID examId, UUID studentId, UUID subjectId);

    List<ExamMark> findByExamIdAndSectionIdOrderByStudentId(UUID examId, UUID sectionId);

    List<ExamMark> findByExamIdAndStudentId(UUID examId, UUID studentId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByExamId(UUID examId);

    /**
     * Per-subject completion counts for a section — powers the class-teacher dashboard's
     * "which subjects are still pending entry" view (LLD §6.1).
     */
    @Query(value = """
        SELECT subject_id AS subject_id,
               COUNT(*)   AS entered_count
        FROM exam_marks
        WHERE exam_id = :examId
          AND section_id = :sectionId
        GROUP BY subject_id
        """, nativeQuery = true)
    List<SubjectCompletionRow> countBySubjectForExamSection(
        @Param("examId") UUID examId, @Param("sectionId") UUID sectionId);

    interface SubjectCompletionRow {
        UUID getSubjectId();
        Long getEnteredCount();
    }
}
