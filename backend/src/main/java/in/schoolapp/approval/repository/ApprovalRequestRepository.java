package in.schoolapp.approval.repository;

import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Optional<ApprovalRequest> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<ApprovalRequest> findBySchoolIdAndStatusOrderByCreatedAtDesc(UUID schoolId, ApprovalStatus status);

    List<ApprovalRequest> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
