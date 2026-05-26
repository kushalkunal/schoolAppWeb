package in.schoolapp.transport;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.transport.entity.StudentTransportAssignment;
import in.schoolapp.transport.entity.TransportRoute;
import in.schoolapp.transport.entity.TransportVehicle;
import in.schoolapp.transport.repository.StudentTransportAssignmentRepository;
import in.schoolapp.transport.repository.TransportRouteRepository;
import in.schoolapp.transport.repository.TransportVehicleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal CRUD for transport routes / vehicles / student-assignments. GPS tracking and
 * trip-event streaming deferred to a later mobile slice — the entities here capture the
 * "who-rides-which-bus-where" relationships.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/transport")
@RequiredArgsConstructor
public class TransportController {

    private final TransportRouteRepository routeRepo;
    private final TransportVehicleRepository vehicleRepo;
    private final StudentTransportAssignmentRepository assignmentRepo;

    // ---------- Routes ----------

    @GetMapping("/routes")
    public ApiResponse<List<TransportRoute>> listRoutes(@PathVariable UUID tenantId) {
        return ApiResponse.success(routeRepo.findBySchoolIdAndActiveTrueOrderByNameAsc(tenantId));
    }

    public record RouteRequest(
        @jakarta.validation.constraints.NotBlank String name,
        List<Map<String, Object>> stops,
        long farePaise
    ) {}

    @PostMapping("/routes")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<TransportRoute>> createRoute(@PathVariable UUID tenantId,
                                                                    @Valid @RequestBody RouteRequest req) {
        TransportRoute r = new TransportRoute();
        r.setSchoolId(tenantId);
        r.setName(req.name());
        if (req.stops() != null) r.setStops(req.stops());
        r.setFarePaise(req.farePaise());
        r.setActive(true);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(routeRepo.save(r)));
    }

    @DeleteMapping("/routes/{routeId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ApiResponse<Void> deactivateRoute(@PathVariable UUID tenantId, @PathVariable UUID routeId) {
        TransportRoute r = routeRepo.findById(routeId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Route", routeId));
        ensureTenant(r.getSchoolId(), tenantId);
        r.setActive(false);
        routeRepo.save(r);
        return ApiResponse.ok();
    }

    // ---------- Vehicles ----------

    @GetMapping("/vehicles")
    public ApiResponse<List<TransportVehicle>> listVehicles(@PathVariable UUID tenantId) {
        return ApiResponse.success(vehicleRepo.findBySchoolIdAndActiveTrueOrderByRegistrationNoAsc(tenantId));
    }

    public record VehicleRequest(
        @jakarta.validation.constraints.NotBlank String registrationNo,
        UUID driverStaffId,
        int capacity,
        UUID routeId
    ) {}

    @PostMapping("/vehicles")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<TransportVehicle>> createVehicle(@PathVariable UUID tenantId,
                                                                        @Valid @RequestBody VehicleRequest req) {
        TransportVehicle v = new TransportVehicle();
        v.setSchoolId(tenantId);
        v.setRegistrationNo(req.registrationNo());
        v.setDriverStaffId(req.driverStaffId());
        v.setCapacity(req.capacity() <= 0 ? 30 : req.capacity());
        v.setRouteId(req.routeId());
        v.setActive(true);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(vehicleRepo.save(v)));
    }

    // ---------- Assignments ----------

    @GetMapping("/assignments/students/{studentId}")
    public ApiResponse<List<StudentTransportAssignment>> activeForStudent(@PathVariable UUID tenantId,
                                                                          @PathVariable UUID studentId) {
        return ApiResponse.success(assignmentRepo.findByStudentIdAndEndDateIsNull(studentId).stream()
            .filter(a -> a.getSchoolId().equals(tenantId)).toList());
    }

    public record AssignRequest(
        @jakarta.validation.constraints.NotNull UUID studentId,
        @jakarta.validation.constraints.NotNull UUID routeId,
        String stopName,
        @jakarta.validation.constraints.NotNull LocalDate startDate
    ) {}

    @PostMapping("/assignments")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<StudentTransportAssignment>> assign(@PathVariable UUID tenantId,
                                                                          @Valid @RequestBody AssignRequest req) {
        // End any current ongoing assignment for this student.
        assignmentRepo.findByStudentIdAndEndDateIsNull(req.studentId()).forEach(a -> {
            if (a.getSchoolId().equals(tenantId)) {
                a.setEndDate(req.startDate().minusDays(1));
                assignmentRepo.save(a);
            }
        });
        StudentTransportAssignment a = new StudentTransportAssignment();
        a.setSchoolId(tenantId);
        a.setStudentId(req.studentId());
        a.setRouteId(req.routeId());
        a.setStopName(req.stopName());
        a.setStartDate(req.startDate());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(assignmentRepo.save(a)));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ApiResponse<Void> endAssignment(@PathVariable UUID tenantId, @PathVariable UUID assignmentId) {
        StudentTransportAssignment a = assignmentRepo.findById(assignmentId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Assignment", assignmentId));
        ensureTenant(a.getSchoolId(), tenantId);
        a.setEndDate(LocalDate.now());
        assignmentRepo.save(a);
        return ApiResponse.ok();
    }

    private static void ensureTenant(UUID rowTenant, UUID requestTenant) {
        if (!rowTenant.equals(requestTenant)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Resource not in this school");
        }
    }
}
