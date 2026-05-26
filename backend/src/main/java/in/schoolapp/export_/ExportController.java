package in.schoolapp.export_;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Data-portability endpoints (gap §8). Each route streams the response body directly — the
 * file never materialises in server memory and the client sees bytes flowing as pages are
 * paginated through the DB.
 * <p>
 * Restricted to owner/admin roles: exports contain PII + financial data so a chronic-teacher
 * role should not be able to walk away with the full dataset. DPDP Act 2023 expects a signed
 * audit trail for each export — {@link ExportService} calls {@code auditLogger.logAction} with
 * the actor's staff id on every export.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/export")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.DATA_EXPORT)
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/students")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public void exportStudents(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "XLSX") ExportFormat format,
        HttpServletResponse response
    ) throws IOException {
        prepareResponse(response, format, "students");
        try (OutputStream out = response.getOutputStream()) {
            exportService.exportStudents(tenantId, format, out);
        }
    }

    @GetMapping("/fees")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public void exportFeePayments(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "XLSX") ExportFormat format,
        HttpServletResponse response
    ) throws IOException {
        prepareResponse(response, format, "fee-payments");
        try (OutputStream out = response.getOutputStream()) {
            exportService.exportFeePayments(tenantId, format, out);
        }
    }

    @GetMapping("/attendance")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public void exportAttendance(
        @PathVariable UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "XLSX") ExportFormat format,
        HttpServletResponse response
    ) throws IOException {
        if (from == null || to == null || from.isAfter(to)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "from must be <= to");
        }
        prepareResponse(response, format, "attendance-" + from + "-to-" + to);
        try (OutputStream out = response.getOutputStream()) {
            exportService.exportAttendance(tenantId, from, to, format, out);
        }
    }

    private static void prepareResponse(HttpServletResponse response, ExportFormat format, String baseName) {
        if (format == ExportFormat.CSV) {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + baseName + ".csv\"");
        } else {
            response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + baseName + ".xlsx\"");
        }
        // Hint proxies not to buffer — lets the client see progress on multi-minute exports.
        response.setHeader("X-Accel-Buffering", "no");
    }
}
