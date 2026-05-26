package in.schoolapp.student.repository;

import in.schoolapp.student.entity.StudentParentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentParentLinkRepository extends JpaRepository<StudentParentLink, UUID> {

    List<StudentParentLink> findByStudentId(UUID studentId);

    List<StudentParentLink> findByParentId(UUID parentId);

    Optional<StudentParentLink> findByStudentIdAndPrimaryTrue(UUID studentId);
}
