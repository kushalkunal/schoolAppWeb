package in.schoolapp.cafeteria;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.cafeteria.dto.PlaceOrderRequest;
import in.schoolapp.cafeteria.entity.CafeteriaOrder;
import in.schoolapp.cafeteria.entity.MenuItem;
import in.schoolapp.cafeteria.entity.OrderItem;
import in.schoolapp.cafeteria.entity.Wallet;
import in.schoolapp.cafeteria.entity.WalletTransaction;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/cafeteria")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.CAFETERIA)
public class CafeteriaController {

    private final CafeteriaService service;

    // ---------- menu ----------

    @PostMapping("/menu")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<MenuItem>> createMenuItem(
        @PathVariable UUID tenantId, @Valid @RequestBody MenuItem body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createMenuItem(tenantId, body)));
    }

    @GetMapping("/menu")
    public ApiResponse<List<MenuItem>> listMenu(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "true") boolean availableOnly) {
        return ApiResponse.success(service.listMenu(tenantId, availableOnly));
    }

    // ---------- wallet ----------

    @GetMapping("/wallets/{studentId}")
    public ApiResponse<Wallet> wallet(@PathVariable UUID tenantId, @PathVariable UUID studentId) {
        return ApiResponse.success(service.getOrCreateWallet(tenantId, studentId));
    }

    @PostMapping("/wallets/{studentId}/topup")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<WalletTransaction>> topUp(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @RequestParam long amountPaise,
        @RequestParam(required = false) String notes
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.topUp(tenantId, studentId, amountPaise, notes)));
    }

    @GetMapping("/wallets/{walletId}/transactions")
    public ApiResponse<List<WalletTransaction>> transactions(
        @PathVariable UUID tenantId, @PathVariable UUID walletId) {
        return ApiResponse.success(service.listTransactions(tenantId, walletId));
    }

    // ---------- orders ----------

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<CafeteriaOrder>> place(
        @PathVariable UUID tenantId, @Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.placeOrder(tenantId, req)));
    }

    @PostMapping("/orders/{orderId}/fulfill")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<CafeteriaOrder> fulfill(
        @PathVariable UUID tenantId, @PathVariable UUID orderId) {
        return ApiResponse.success(service.markFulfilled(tenantId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<CafeteriaOrder> cancel(
        @PathVariable UUID tenantId,
        @PathVariable UUID orderId,
        @RequestParam(required = false) String reason) {
        return ApiResponse.success(service.cancelOrder(tenantId, orderId, reason));
    }

    @GetMapping("/orders")
    public ApiResponse<List<CafeteriaOrder>> listOrders(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listOrders(tenantId));
    }

    @GetMapping("/orders/student/{studentId}")
    public ApiResponse<List<CafeteriaOrder>> listStudentOrders(
        @PathVariable UUID tenantId, @PathVariable UUID studentId) {
        return ApiResponse.success(service.listStudentOrders(tenantId, studentId));
    }

    @GetMapping("/orders/{orderId}/items")
    public ApiResponse<List<OrderItem>> orderItems(
        @PathVariable UUID tenantId, @PathVariable UUID orderId) {
        return ApiResponse.success(service.listOrderItems(tenantId, orderId));
    }
}
