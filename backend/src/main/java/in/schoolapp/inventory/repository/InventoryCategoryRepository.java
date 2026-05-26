package in.schoolapp.inventory.repository;

import in.schoolapp.inventory.entity.InventoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCategoryRepository extends JpaRepository<InventoryCategory, UUID> {
    Optional<InventoryCategory> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<InventoryCategory> findBySchoolIdOrderByNameAsc(UUID schoolId);
}
