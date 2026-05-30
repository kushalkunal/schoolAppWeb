package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamComponentMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamComponentMarkRepository extends JpaRepository<ExamComponentMark, UUID> {

    Optional<ExamComponentMark> findByConfigIdAndStudentId(UUID configId, UUID studentId);

    List<ExamComponentMark> findByConfigIdAndSectionId(UUID configId, UUID sectionId);

    /** All component marks for a section across all configs belonging to a given exam. */
    @Query("""
        SELECT m FROM ExamComponentMark m
        WHERE m.sectionId = :sectionId
          AND m.configId IN (
              SELECT c.id FROM ExamSubjectConfig c WHERE c.examId = :examId
          )
        """)
    List<ExamComponentMark> findByExamIdAndSectionId(@Param("examId") UUID examId,
                                                     @Param("sectionId") UUID sectionId);

    /** All component marks for one student across all configs of an exam. */
    @Query("""
        SELECT m FROM ExamComponentMark m
        WHERE m.studentId = :studentId
          AND m.configId IN (
              SELECT c.id FROM ExamSubjectConfig c WHERE c.examId = :examId
          )
        """)
    List<ExamComponentMark> findByExamIdAndStudentId(@Param("examId") UUID examId,
                                                     @Param("studentId") UUID studentId);

    /** Count of entries that are NOT yet entered (obtained is null and not absent) for a section. */
    @Query("""
        SELECT COUNT(m) FROM ExamComponentMark m
        WHERE m.sectionId = :sectionId
          AND m.configId IN (
              SELECT c.id FROM ExamSubjectConfig c WHERE c.examId = :examId
          )
          AND m.obtained IS NULL
          AND m.absent = false
        """)
    long countPendingForSection(@Param("examId") UUID examId, @Param("sectionId") UUID sectionId);
}
