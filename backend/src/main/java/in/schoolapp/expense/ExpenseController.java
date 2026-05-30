package in.schoolapp.expense;

import in.schoolapp.approval.dto.ApprovalRequestResponse;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.expense.dto.CategoryDto;
import in.schoolapp.expense.dto.CreateExpenseRequest;
import in.schoolapp.expense.dto.ExpenseResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/expenses")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.EXPENSE_TRACKING)
public class ExpenseController {

    private final ExpenseService service;

    @GetMapping("/categories")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<List<CategoryDto>> categories(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listCategories(tenantId));
    }

    @PostMapping("/categories")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(
        @PathVariable UUID tenantId,
        @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(service.createCategory(tenantId, body.get("name"))));
    }

    @PostMapping
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateExpenseRequest req
    ) {
        // Returns a PENDING approval; the expense is excluded from totals until approved.
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.create(tenantId, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ApiResponse.ok();
    }

    @GetMapping
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<Page<ExpenseResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success(service.list(tenantId, page, size));
    }

    @GetMapping("/sum")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<Long> sum(
        @PathVariable UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(service.sumBetween(tenantId, from, to));
    }
}
