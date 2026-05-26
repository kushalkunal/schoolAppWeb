package in.schoolapp.cashrecon;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.cashrecon.dto.CashReconciliationResponse;
import in.schoolapp.cashrecon.dto.CloseDrawerRequest;
import in.schoolapp.cashrecon.dto.DayTotalsResponse;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/cash-recon")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.CASH_RECONCILIATION)
public class CashReconciliationController {

    private final CashReconciliationService service;

    /** "What should be in the drawer for today?" */
    @GetMapping("/expected")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<DayTotalsResponse> expected(
        @PathVariable UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(service.expectedForDay(tenantId, date));
    }

    /** Cashier closes the drawer with the physical counts. */
    @PostMapping("/close")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<CashReconciliationResponse>> close(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CloseDrawerRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.closeDrawer(tenantId, req)));
    }

    @GetMapping("/history")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<List<CashReconciliationResponse>> history(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.history(tenantId));
    }
}
