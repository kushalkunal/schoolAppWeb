package in.schoolapp.fee;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.fee.dto.BulkReminderRequest;
import in.schoolapp.fee.dto.ClassCollectionRow;
import in.schoolapp.fee.dto.CreateInvoiceRequest;
import in.schoolapp.fee.dto.DefaulterResponse;
import in.schoolapp.fee.dto.FeeDashboardResponse;
import in.schoolapp.fee.dto.InvoiceResponse;
import in.schoolapp.fee.dto.OpeningBalanceRequest;
import in.schoolapp.fee.dto.PaymentResponse;
import in.schoolapp.fee.dto.QuickCollectRequest;
import in.schoolapp.fee.dto.RecentPaymentRow;
import in.schoolapp.fee.dto.StudentFeeSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.FEE)
public class FeeController {

    private final FeePaymentService feePaymentService;
    private final FeeInvoiceService feeInvoiceService;
    private final FeeDashboardService feeDashboardService;
    private final FeeReminderService feeReminderService;

    // ----- Quick collect (zero-config payment) -----

    @PostMapping("/fees/payments")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<PaymentResponse>> quickCollect(
        @PathVariable UUID tenantId,
        @Valid @RequestBody QuickCollectRequest request
    ) {
        PaymentResponse response = feePaymentService.quickCollect(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/fees/payments/{paymentId}")
    public ApiResponse<PaymentResponse> getPayment(
        @PathVariable UUID tenantId,
        @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(feePaymentService.getPayment(tenantId, paymentId));
    }

    // ----- Invoices -----

    @PostMapping("/fees/invoices")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateInvoiceRequest request
    ) {
        InvoiceResponse response = feeInvoiceService.createInvoice(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/fees/opening-balances")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> recordOpeningBalances(
        @PathVariable UUID tenantId,
        @Valid @RequestBody OpeningBalanceRequest request
    ) {
        List<InvoiceResponse> created = feeInvoiceService.recordOpeningBalances(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ----- Student-scoped summary -----

    @GetMapping("/students/{studentId}/fee-summary")
    public ApiResponse<StudentFeeSummaryResponse> getStudentFeeSummary(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId
    ) {
        return ApiResponse.success(feePaymentService.getStudentSummary(tenantId, studentId));
    }

    // ----- Dashboard + defaulters -----

    @GetMapping("/fees/dashboard")
    public ApiResponse<FeeDashboardResponse> getDashboard(@PathVariable UUID tenantId) {
        return ApiResponse.success(feeDashboardService.getDashboard(tenantId));
    }

    @GetMapping("/fees/defaulters")
    public ApiResponse<List<DefaulterResponse>> listDefaulters(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success(feeDashboardService.listDefaulters(tenantId, page, size));
    }

    // ----- Reports -----

    @GetMapping("/fees/payments/recent")
    public ApiResponse<List<RecentPaymentRow>> recentPayments(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(feeDashboardService.listRecentPayments(tenantId, Math.min(size, 100)));
    }

    @GetMapping("/fees/reports/class-wise")
    public ApiResponse<List<ClassCollectionRow>> classWiseReport(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end   = to   != null ? to   : today;
        return ApiResponse.success(feeDashboardService.classWiseReport(tenantId, start, end));
    }

    /** Daily/range collection register (audit #21): per-mode breakdown + totals. Defaults to today. */
    @GetMapping("/fees/reports/collection-register")
    public ApiResponse<in.schoolapp.fee.dto.CollectionRegisterResponse> collectionRegister(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today;
        LocalDate end   = to   != null ? to   : today;
        return ApiResponse.success(feeDashboardService.collectionRegister(tenantId, start, end));
    }

    // ----- Manual reminders -----

    @PostMapping("/fees/reminders")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<Map<String, Integer>> sendReminders(
        @PathVariable UUID tenantId,
        @Valid @RequestBody BulkReminderRequest request
    ) {
        int queued = feeReminderService.queueBulkReminders(tenantId, request);
        return ApiResponse.success(Map.of("queued", queued));
    }
}
