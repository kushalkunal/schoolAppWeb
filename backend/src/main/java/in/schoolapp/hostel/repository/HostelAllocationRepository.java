package in.schoolapp.hostel.repository;

import in.schoolapp.hostel.entity.HostelAllocation;
import in.schoolapp.hostel.entity.HostelAllocation.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostelAllocationRepository extends JpaRepository<HostelAllocation, UUID> {
    Optional<HostelAllocation> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<HostelAllocation> findByStudentIdAndStatus(UUID studentId, Status status);
    List<HostelAllocation> findByRoomIdAndStatus(UUID roomId, Status status);
    List<HostelAllocation> findBySchoolIdAndStatusOrderByAllocatedFromDesc(UUID schoolId, Status status);
}
