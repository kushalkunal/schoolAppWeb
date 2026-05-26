package in.schoolapp.fee;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.fee.dto.FeeReminderScheduleDto;
import in.schoolapp.fee.entity.FeeReminderSchedule;
import in.schoolapp.fee.repository.FeeReminderScheduleRepository;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-configurable reminder ladder. Keeps the controller + service in one file because the
 * service layer is thin — it's just CRUD on the schedule table. The actual sending is done by
 * {@link FeeReminderSchedulerService}'s cron.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fee-reminder-schedules")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.FEE)
public class FeeReminderScheduleController {

    private final FeeReminderScheduleRepository repository;
    private final AuditLogger auditLogger;
    private final FeeReminderSchedulerService schedulerService;

    @GetMapping
    public ApiResponse<List<FeeReminderScheduleDto>> list(@PathVariable UUID tenantId) {
        return ApiResponse.success(
            repository.findBySchoolIdOrderByCreatedAtAsc(tenantId).stream()
                .map(FeeReminderScheduleDto::from)
                .toList());
    }

    @PostMapping
    @PreAuthorize(AppRoles.FEE_WRITER)
    @Transactional
    public ResponseEntity<ApiResponse<FeeReminderScheduleDto>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody FeeReminderScheduleDto dto
    ) {
        FeeReminderSchedule s = new FeeReminderSchedule();
        s.setSchoolId(tenantId);
        s.setName(dto.name().trim());
        s.setTriggerType(dto.triggerType());
        s.setDaysOffset(dto.daysOffset());
        s.setIncludeUpiLink(dto.includeUpiLink());
        s.setActive(dto.active());
        s = repository.save(s);
        auditLogger.logCreate(tenantId, "FeeReminderSchedule", s.getId(), Map.of(
            "name", s.getName(),
            "triggerType", s.getTriggerType().name(),
            "daysOffset", s.getDaysOffset()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(FeeReminderScheduleDto.from(s)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AppRoles.FEE_WRITER)
    @Transactional
    public ApiResponse<FeeReminderScheduleDto> update(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody FeeReminderScheduleDto dto
    ) {
        FeeReminderSchedule s = repository.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "FeeReminderSchedule", id));
        s.setName(dto.name().trim());
        s.setTriggerType(dto.triggerType());
        s.setDaysOffset(dto.daysOffset());
        s.setIncludeUpiLink(dto.includeUpiLink());
        s.setActive(dto.active());
        repository.save(s);
        auditLogger.logUpdate(tenantId, "FeeReminderSchedule", id, Map.of(), Map.of(
            "name", s.getName(),
            "triggerType", s.getTriggerType().name(),
            "daysOffset", s.getDaysOffset(),
            "active", s.isActive()
        ));
        return ApiResponse.success(FeeReminderScheduleDto.from(s));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.FEE_WRITER)
    @Transactional
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        FeeReminderSchedule s = repository.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "FeeReminderSchedule", id));
        repository.delete(s);
        auditLogger.logDelete(tenantId, "FeeReminderSchedule", id, Map.of("name", s.getName()));
        return ApiResponse.ok();
    }

    /** Manual trigger — useful for testing + a "run now" admin button. */
    @PostMapping("/run-now")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<Map<String, Integer>> runNow(@PathVariable UUID tenantId) {
        int queued = schedulerService.scanTenant(tenantId, java.time.LocalDate.now());
        return ApiResponse.success(Map.of("queued", queued));
    }
}
