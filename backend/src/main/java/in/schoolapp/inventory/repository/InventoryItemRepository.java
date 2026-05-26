package in.schoolapp.inventory.repository;

import in.schoolapp.inventory.entity.InventoryItem;
import in.schoolapp.inventory.entity.InventoryItem.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<InventoryItem> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    List<InventoryItem> findBySchoolIdAndStatusOrderByCreatedAtDesc(UUID schoolId, Status status);
    List<InventoryItem> findBySchoolIdAndCategoryIdOrderByCreatedAtDesc(UUID schoolId, UUID categoryId);
}
