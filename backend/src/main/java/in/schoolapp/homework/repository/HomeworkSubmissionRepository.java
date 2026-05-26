package in.schoolapp.homework.repository;

import in.schoolapp.homework.entity.HomeworkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {
    List<HomeworkSubmission> findByAssignmentId(UUID assignmentId);
    Optional<HomeworkSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<HomeworkSubmission> findByStudentIdOrderBySubmittedAtDesc(UUID studentId);
}
