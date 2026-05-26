package in.schoolapp.inventory.repository;

import in.schoolapp.inventory.entity.InventoryMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryMaintenanceRepository extends JpaRepository<InventoryMaintenance, UUID> {
    List<InventoryMaintenance> findByItemIdOrderByPerformedAtDesc(UUID itemId);
}
