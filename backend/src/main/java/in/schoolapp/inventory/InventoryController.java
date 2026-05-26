package in.schoolapp.inventory;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.inventory.entity.InventoryCategory;
import in.schoolapp.inventory.entity.InventoryIssuance;
import in.schoolapp.inventory.entity.InventoryItem;
import in.schoolapp.inventory.entity.InventoryMaintenance;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inventory")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.INVENTORY)
public class InventoryController {

    private final InventoryService service;

    // categories
    @PostMapping("/categories")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<InventoryCategory>> createCategory(
        @PathVariable UUID tenantId, @Valid @RequestBody InventoryCategory body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createCategory(tenantId, body)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<InventoryCategory>> listCategories(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listCategories(tenantId));
    }

    // items
    @PostMapping("/items")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<InventoryItem>> createItem(
        @PathVariable UUID tenantId, @Valid @RequestBody InventoryItem body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createItem(tenantId, body)));
    }

    @GetMapping("/items")
    public ApiResponse<List<InventoryItem>> listItems(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) InventoryItem.Status status,
        @RequestParam(required = false) UUID categoryId
    ) {
        return ApiResponse.success(service.listItems(tenantId, status, categoryId));
    }

    @GetMapping("/items/{itemId}")
    public ApiResponse<InventoryItem> getItem(
        @PathVariable UUID tenantId, @PathVariable UUID itemId) {
        return ApiResponse.success(service.getItem(tenantId, itemId));
    }

    // issuances
    @PostMapping("/items/{itemId}/issue")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<InventoryIssuance>> issue(
        @PathVariable UUID tenantId,
        @PathVariable UUID itemId,
        @RequestParam(required = false) UUID staffId,
        @RequestParam(required = false) UUID studentId,
        @RequestParam(required = false) OffsetDateTime expectedReturn,
        @RequestParam(required = false) String notes
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.issue(tenantId, itemId, staffId, studentId, expectedReturn, notes)));
    }

    @PostMapping("/issuances/{issuanceId}/return")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<InventoryIssuance> returnItem(
        @PathVariable UUID tenantId,
        @PathVariable UUID issuanceId,
        @RequestParam(required = false) InventoryIssuance.ReturnCondition condition,
        @RequestParam(required = false) String notes
    ) {
        return ApiResponse.success(service.returnItem(tenantId, issuanceId, condition, notes));
    }

    @GetMapping("/items/{itemId}/issuances")
    public ApiResponse<List<InventoryIssuance>> issuancesForItem(
        @PathVariable UUID tenantId, @PathVariable UUID itemId) {
        return ApiResponse.success(service.listIssuancesForItem(tenantId, itemId));
    }

    @GetMapping("/issuances/outstanding")
    public ApiResponse<List<InventoryIssuance>> outstanding(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listOutstandingIssuances(tenantId));
    }

    // maintenance
    @PostMapping("/items/{itemId}/maintenance")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<InventoryMaintenance>> recordMaintenance(
        @PathVariable UUID tenantId,
        @PathVariable UUID itemId,
        @Valid @RequestBody InventoryMaintenance body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.recordMaintenance(tenantId, itemId, body)));
    }

    @GetMapping("/items/{itemId}/maintenance")
    public ApiResponse<List<InventoryMaintenance>> maintenance(
        @PathVariable UUID tenantId, @PathVariable UUID itemId) {
        return ApiResponse.success(service.listMaintenance(tenantId, itemId));
    }
}
