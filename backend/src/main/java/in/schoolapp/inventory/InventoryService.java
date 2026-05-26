package in.schoolapp.inventory;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.inventory.entity.InventoryCategory;
import in.schoolapp.inventory.entity.InventoryIssuance;
import in.schoolapp.inventory.entity.InventoryItem;
import in.schoolapp.inventory.entity.InventoryItem.Status;
import in.schoolapp.inventory.entity.InventoryMaintenance;
import in.schoolapp.inventory.repository.InventoryCategoryRepository;
import in.schoolapp.inventory.repository.InventoryIssuanceRepository;
import in.schoolapp.inventory.repository.InventoryItemRepository;
import in.schoolapp.inventory.repository.InventoryMaintenanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryCategoryRepository categoryRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryIssuanceRepository issuanceRepository;
    private final InventoryMaintenanceRepository maintenanceRepository;

    // ---------------- categories ----------------

    @Transactional
    public InventoryCategory createCategory(UUID tenantId, InventoryCategory template) {
        template.setSchoolId(tenantId);
        return categoryRepository.save(template);
    }

    public List<InventoryCategory> listCategories(UUID tenantId) {
        return categoryRepository.findBySchoolIdOrderByNameAsc(tenantId);
    }

    // ---------------- items ----------------

    @Transactional
    public InventoryItem createItem(UUID tenantId, InventoryItem template) {
        template.setSchoolId(tenantId);
        if (template.getStatus() == null) template.setStatus(Status.AVAILABLE);
        return itemRepository.save(template);
    }

    public List<InventoryItem> listItems(UUID tenantId, Status status, UUID categoryId) {
        if (status != null) {
            return itemRepository.findBySchoolIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        }
        if (categoryId != null) {
            return itemRepository.findBySchoolIdAndCategoryIdOrderByCreatedAtDesc(tenantId, categoryId);
        }
        return itemRepository.findBySchoolIdOrderByCreatedAtDesc(tenantId);
    }

    public InventoryItem getItem(UUID tenantId, UUID itemId) {
        return itemRepository.findByIdAndSchoolId(itemId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Item not found"));
    }

    // ---------------- issue / return ----------------

    /**
     * Issue an item to a staff member OR a student. Exactly one of {@code staffId} /
     * {@code studentId} must be non-null (also enforced by the DB CHECK).
     */
    @Transactional
    public InventoryIssuance issue(UUID tenantId, UUID itemId, UUID staffId, UUID studentId,
                                   OffsetDateTime expectedReturn, String notes) {
        if ((staffId == null) == (studentId == null)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Provide exactly one of staffId or studentId");
        }
        InventoryItem item = getItem(tenantId, itemId);
        if (item.getStatus() != Status.AVAILABLE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Item is " + item.getStatus() + ", cannot issue");
        }
        InventoryIssuance iss = new InventoryIssuance();
        iss.setSchoolId(tenantId);
        iss.setItemId(itemId);
        iss.setIssuedToStaffId(staffId);
        iss.setIssuedToStudentId(studentId);
        iss.setExpectedReturnAt(expectedReturn);
        iss.setNotes(notes);
        iss = issuanceRepository.save(iss);

        item.setStatus(Status.ISSUED);
        itemRepository.save(item);
        log.info("Inventory issued tenant={} item={} to staff={} student={}",
            tenantId, itemId, staffId, studentId);
        return iss;
    }

    @Transactional
    public InventoryIssuance returnItem(UUID tenantId, UUID issuanceId,
                                        InventoryIssuance.ReturnCondition condition,
                                        String notes) {
        InventoryIssuance iss = issuanceRepository.findByIdAndSchoolId(issuanceId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Issuance not found"));
        if (iss.getReturnedAt() != null) return iss;

        iss.setReturnedAt(OffsetDateTime.now());
        iss.setReturnCondition(condition != null ? condition : InventoryIssuance.ReturnCondition.GOOD);
        if (notes != null) iss.setNotes(notes);
        issuanceRepository.save(iss);

        InventoryItem item = getItem(tenantId, iss.getItemId());
        item.setStatus(condition == InventoryIssuance.ReturnCondition.LOST
            ? Status.LOST
            : condition == InventoryIssuance.ReturnCondition.DAMAGED
                ? Status.UNDER_MAINTENANCE
                : Status.AVAILABLE);
        itemRepository.save(item);
        return iss;
    }

    public List<InventoryIssuance> listIssuancesForItem(UUID tenantId, UUID itemId) {
        getItem(tenantId, itemId);    // scope check
        return issuanceRepository.findByItemIdOrderByIssuedAtDesc(itemId);
    }

    public List<InventoryIssuance> listOutstandingIssuances(UUID tenantId) {
        return issuanceRepository.findBySchoolIdAndReturnedAtIsNullOrderByIssuedAtDesc(tenantId);
    }

    // ---------------- maintenance ----------------

    @Transactional
    public InventoryMaintenance recordMaintenance(UUID tenantId, UUID itemId, InventoryMaintenance template) {
        InventoryItem item = getItem(tenantId, itemId);
        template.setSchoolId(tenantId);
        template.setItemId(itemId);
        if (template.getPerformedAt() == null) template.setPerformedAt(OffsetDateTime.now());
        InventoryMaintenance saved = maintenanceRepository.save(template);

        // Optionally flip the item back from UNDER_MAINTENANCE → AVAILABLE.
        if (item.getStatus() == Status.UNDER_MAINTENANCE) {
            item.setStatus(Status.AVAILABLE);
            itemRepository.save(item);
        }
        return saved;
    }

    public List<InventoryMaintenance> listMaintenance(UUID tenantId, UUID itemId) {
        getItem(tenantId, itemId);
        return maintenanceRepository.findByItemIdOrderByPerformedAtDesc(itemId);
    }
}
