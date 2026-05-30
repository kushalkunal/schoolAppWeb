package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamSubjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExamSubjectConfigRepository extends JpaRepository<ExamSubjectConfig, UUID> {

    List<ExamSubjectConfig> findByExamIdOrderBySubjectIdAscSortOrderAsc(UUID examId);

    List<ExamSubjectConfig> findByExamIdAndSubjectIdOrderBySortOrderAsc(UUID examId, UUID subjectId);

    boolean existsByExamIdAndSubjectId(UUID examId, UUID subjectId);

    @Modifying
    @Query("DELETE FROM ExamSubjectConfig c WHERE c.examId = :examId AND c.subjectId = :subjectId")
    void deleteByExamIdAndSubjectId(@Param("examId") UUID examId, @Param("subjectId") UUID subjectId);

    /** All subject IDs that have at least one config for this exam. */
    @Query("SELECT DISTINCT c.subjectId FROM ExamSubjectConfig c WHERE c.examId = :examId")
    List<UUID> findDistinctSubjectIdsByExamId(@Param("examId") UUID examId);
}
