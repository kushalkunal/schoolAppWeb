package in.schoolapp.hostel.repository;

import in.schoolapp.hostel.entity.HostelRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostelRoomRepository extends JpaRepository<HostelRoom, UUID> {
    Optional<HostelRoom> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<HostelRoom> findByHostelIdOrderByFloorAscRoomNumberAsc(UUID hostelId);
}
