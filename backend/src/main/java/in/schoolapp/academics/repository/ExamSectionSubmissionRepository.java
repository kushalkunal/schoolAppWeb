package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamSectionSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamSectionSubmissionRepository extends JpaRepository<ExamSectionSubmission, UUID> {

    Optional<ExamSectionSubmission> findByExamIdAndSectionId(UUID examId, UUID sectionId);

    boolean existsByExamIdAndSectionId(UUID examId, UUID sectionId);
}
