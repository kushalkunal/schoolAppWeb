package in.schoolapp.communication.parent;

import in.schoolapp.communication.parent.entity.ParentMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParentMessageRepository extends JpaRepository<ParentMessage, UUID> {
    List<ParentMessage> findByStudentIdOrderBySentAtDesc(UUID studentId);
    Page<ParentMessage> findBySchoolIdOrderBySentAtDesc(UUID schoolId, Pageable page);
}
