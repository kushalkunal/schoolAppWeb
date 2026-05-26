package in.schoolapp.inventory.repository;

import in.schoolapp.inventory.entity.InventoryIssuance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryIssuanceRepository extends JpaRepository<InventoryIssuance, UUID> {
    Optional<InventoryIssuance> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<InventoryIssuance> findByItemIdOrderByIssuedAtDesc(UUID itemId);
    List<InventoryIssuance> findBySchoolIdAndReturnedAtIsNullOrderByIssuedAtDesc(UUID schoolId);
}
