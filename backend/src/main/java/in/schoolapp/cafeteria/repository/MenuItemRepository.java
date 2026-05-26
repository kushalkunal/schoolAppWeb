package in.schoolapp.cafeteria.repository;

import in.schoolapp.cafeteria.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    Optional<MenuItem> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<MenuItem> findBySchoolIdAndAvailableOrderByCategoryAscNameAsc(UUID schoolId, boolean available);
    List<MenuItem> findBySchoolIdOrderByCategoryAscNameAsc(UUID schoolId);
}
