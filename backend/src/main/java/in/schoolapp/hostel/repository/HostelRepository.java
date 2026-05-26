package in.schoolapp.hostel.repository;

import in.schoolapp.hostel.entity.Hostel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostelRepository extends JpaRepository<Hostel, UUID> {
    Optional<Hostel> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<Hostel> findBySchoolIdAndActiveOrderByNameAsc(UUID schoolId, boolean active);
}
