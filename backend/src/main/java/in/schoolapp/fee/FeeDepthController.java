package in.schoolapp.fee;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.fee.dto.CreateDiscountRequest;
import in.schoolapp.fee.dto.DiscountResponse;
import in.schoolapp.fee.dto.InstallmentPlanRequest;
import in.schoolapp.fee.dto.InstallmentPlanResponse;
import in.schoolapp.fee.dto.RefundRequest;
import in.schoolapp.fee.dto.RefundResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slice 15 surface: discounts + refunds + installment plans. Each endpoint is
 * gated by its own {@link FeatureKey} so plans without the capability return
 * {@code FEATURE_DISABLED}.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
public class FeeDepthController {

    private final FeeDiscountService discountService;
    private final FeeRefundService refundService;
    private final FeeInstallmentService installmentService;

    // ---------------- Discounts ----------------

    @PostMapping("/fee-discounts")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.FEE_DISCOUNTS)
    public ResponseEntity<ApiResponse<DiscountResponse>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateDiscountRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(discountService.create(tenantId, req)));
    }

    @GetMapping("/students/{studentId}/fee-discounts")
    @RequiresFeature(FeatureKey.FEE_DISCOUNTS)
    public ApiResponse<List<DiscountResponse>> list(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId
    ) {
        return ApiResponse.success(discountService.list(tenantId, studentId));
    }

    @DeleteMapping("/fee-discounts/{discountId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.FEE_DISCOUNTS)
    public ApiResponse<Map<String, Object>> deactivate(
        @PathVariable UUID tenantId,
        @PathVariable UUID discountId
    ) {
        discountService.deactivate(tenantId, discountId);
        return ApiResponse.success(Map.of("deactivated", discountId));
    }

    // ---------------- Refunds ----------------

    @PostMapping("/fee-payments/{paymentId}/refund")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.FEE_REFUNDS)
    public ResponseEntity<ApiResponse<RefundResponse>> refund(
        @PathVariable UUID tenantId,
        @PathVariable UUID paymentId,
        @Valid @RequestBody RefundRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(refundService.refund(tenantId, paymentId, req)));
    }

    // ---------------- Installment plans ----------------

    @PostMapping("/fee-invoices/{invoiceId}/installments")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.FEE_INSTALLMENTS)
    public ResponseEntity<ApiResponse<InstallmentPlanResponse>> split(
        @PathVariable UUID tenantId,
        @PathVariable UUID invoiceId,
        @Valid @RequestBody InstallmentPlanRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(installmentService.splitInvoice(tenantId, invoiceId, req)));
    }
}
