package in.schoolapp.homework.repository;

import in.schoolapp.homework.entity.HomeworkAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HomeworkAssignmentRepository extends JpaRepository<HomeworkAssignment, UUID> {
    List<HomeworkAssignment> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    List<HomeworkAssignment> findBySectionIdOrderByDueDateDesc(UUID sectionId);
}
