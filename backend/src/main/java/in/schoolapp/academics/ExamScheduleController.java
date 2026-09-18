package in.schoolapp.academics;

import in.schoolapp.academics.dto.ExamScheduleDtos.ScheduleRow;
import in.schoolapp.academics.dto.ExamScheduleDtos.UpsertScheduleRequest;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Examination timetable for an exam (Step 6). */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/exams/{examId}/schedule")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.ACADEMICS)
public class ExamScheduleController {

    private final ExamScheduleService scheduleService;

    @GetMapping
    public ApiResponse<List<ScheduleRow>> get(@PathVariable UUID tenantId, @PathVariable UUID examId) {
        return ApiResponse.success(scheduleService.getSchedule(tenantId, examId));
    }

    @PutMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<ScheduleRow>> upsert(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @Valid @RequestBody UpsertScheduleRequest req
    ) {
        return ApiResponse.success(scheduleService.upsert(tenantId, examId, req));
    }
}
