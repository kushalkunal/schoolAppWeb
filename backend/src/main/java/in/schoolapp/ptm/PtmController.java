package in.schoolapp.ptm;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.ptm.entity.PtmBooking;
import in.schoolapp.ptm.entity.PtmSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ptm")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.PTM_SCHEDULING)
public class PtmController {

    private final PtmService service;

    @PostMapping("/slots")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<PtmSlot>> createSlot(
        @PathVariable UUID tenantId,
        @RequestBody Map<String, Object> body
    ) {
        UUID teacherId = UUID.fromString((String) body.get("teacherId"));
        UUID sectionId = body.get("sectionId") != null ? UUID.fromString((String) body.get("sectionId")) : null;
        LocalDate date = LocalDate.parse((String) body.get("date"));
        LocalTime start = LocalTime.parse((String) body.get("startTime"));
        LocalTime end = LocalTime.parse((String) body.get("endTime"));
        int capacity = body.get("capacity") == null ? 1 : ((Number) body.get("capacity")).intValue();
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(service.createSlot(tenantId, teacherId, sectionId, date, start, end, capacity)));
    }

    @GetMapping("/slots")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<PtmSlot>> slots(
        @PathVariable UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(service.slotsForDate(tenantId, date));
    }

    @PostMapping("/slots/{slotId}/book")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<PtmBooking>> book(
        @PathVariable UUID tenantId, @PathVariable UUID slotId,
        @RequestBody Map<String, String> body
    ) {
        UUID studentId = UUID.fromString(body.get("studentId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(service.book(tenantId, slotId, studentId, body.get("notes"))));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> cancel(@PathVariable UUID tenantId, @PathVariable UUID bookingId) {
        service.cancel(tenantId, bookingId);
        return ApiResponse.ok();
    }

    @GetMapping("/bookings/by-student/{studentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<PtmBooking>> byStudent(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(service.bookingsForStudent(tenantId, studentId));
    }
}
