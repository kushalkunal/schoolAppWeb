package in.schoolapp.calendar;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.calendar.dto.CalendarDtos.CalendarResponse;
import in.schoolapp.calendar.dto.CalendarDtos.CreateHolidayRequest;
import in.schoolapp.calendar.dto.CalendarDtos.HolidayResponse;
import in.schoolapp.calendar.dto.CalendarDtos.WorkingDaysRequest;
import in.schoolapp.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Per-tenant school calendar. Reads are open to any authenticated staff member (the timetable,
 * attendance and dashboards consume it); writes are admin-only.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/calendar")
@RequiredArgsConstructor
public class SchoolCalendarController {

    private final SchoolCalendarService service;

    @GetMapping
    public ApiResponse<CalendarResponse> getCalendar(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.getCalendar(tenantId));
    }

    @PutMapping("/working-days")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<CalendarResponse> setWorkingDays(
        @PathVariable UUID tenantId,
        @Valid @RequestBody WorkingDaysRequest req
    ) {
        return ApiResponse.success(service.setWorkingDays(tenantId, req.days()));
    }

    @PostMapping("/holidays")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<HolidayResponse> addHoliday(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateHolidayRequest req
    ) {
        return ApiResponse.success(service.addHoliday(tenantId, req.date(), req.name(), req.type()));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> deleteHoliday(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.deleteHoliday(tenantId, id);
        return ApiResponse.ok();
    }
}
