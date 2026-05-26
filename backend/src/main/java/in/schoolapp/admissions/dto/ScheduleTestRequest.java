package in.schoolapp.admissions.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ScheduleTestRequest(
    @NotNull OffsetDateTime scheduledAt,
    @Size(max = 500) String venue
) {}
