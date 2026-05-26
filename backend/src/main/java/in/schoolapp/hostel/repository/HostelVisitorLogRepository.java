package in.schoolapp.hostel.repository;

import in.schoolapp.hostel.entity.HostelVisitorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostelVisitorLogRepository extends JpaRepository<HostelVisitorLog, UUID> {
    Optional<HostelVisitorLog> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<HostelVisitorLog> findBySchoolIdAndOutTimeIsNullOrderByInTimeDesc(UUID schoolId);
    List<HostelVisitorLog> findBySchoolIdOrderByInTimeDesc(UUID schoolId);
}
