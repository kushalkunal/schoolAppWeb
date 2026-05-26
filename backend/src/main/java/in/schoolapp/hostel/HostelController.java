package in.schoolapp.hostel;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.hostel.entity.Hostel;
import in.schoolapp.hostel.entity.HostelAllocation;
import in.schoolapp.hostel.entity.HostelRoom;
import in.schoolapp.hostel.entity.HostelVisitorLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/hostel")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.HOSTEL)
public class HostelController {

    private final HostelService service;

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<Hostel>> create(
        @PathVariable UUID tenantId, @Valid @RequestBody Hostel body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createHostel(tenantId, body)));
    }

    @GetMapping
    public ApiResponse<List<Hostel>> list(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listHostels(tenantId));
    }

    @PostMapping("/{hostelId}/rooms")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<HostelRoom>> createRoom(
        @PathVariable UUID tenantId,
        @PathVariable UUID hostelId,
        @Valid @RequestBody HostelRoom body
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createRoom(tenantId, hostelId, body)));
    }

    @GetMapping("/{hostelId}/rooms")
    public ApiResponse<List<HostelRoom>> listRooms(
        @PathVariable UUID tenantId, @PathVariable UUID hostelId) {
        return ApiResponse.success(service.listRooms(tenantId, hostelId));
    }

    @PostMapping("/rooms/{roomId}/allocate")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<HostelAllocation>> allocate(
        @PathVariable UUID tenantId,
        @PathVariable UUID roomId,
        @RequestParam UUID studentId,
        @RequestParam(required = false) LocalDate from
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.allocate(tenantId, roomId, studentId, from)));
    }

    @PostMapping("/allocations/{allocationId}/vacate")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<HostelAllocation> vacate(
        @PathVariable UUID tenantId,
        @PathVariable UUID allocationId,
        @RequestParam(required = false) String reason
    ) {
        return ApiResponse.success(service.vacate(tenantId, allocationId, reason));
    }

    @GetMapping("/allocations/active")
    public ApiResponse<List<HostelAllocation>> activeAllocations(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listActiveAllocations(tenantId));
    }

    @PostMapping("/visitors/sign-in")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<HostelVisitorLog>> signIn(
        @PathVariable UUID tenantId, @Valid @RequestBody HostelVisitorLog body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.signIn(tenantId, body)));
    }

    @PostMapping("/visitors/{logId}/sign-out")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<HostelVisitorLog> signOut(
        @PathVariable UUID tenantId, @PathVariable UUID logId) {
        return ApiResponse.success(service.signOut(tenantId, logId));
    }

    @GetMapping("/visitors/inside")
    public ApiResponse<List<HostelVisitorLog>> inside(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listCurrentlyInside(tenantId));
    }

    @GetMapping("/visitors")
    public ApiResponse<List<HostelVisitorLog>> allVisits(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listAllVisits(tenantId));
    }
}
