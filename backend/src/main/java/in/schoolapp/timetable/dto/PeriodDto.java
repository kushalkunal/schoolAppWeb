package in.schoolapp.timetable.dto;

import in.schoolapp.timetable.entity.TimetablePeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public record PeriodDto(
    UUID id,                             // null on create
    @NotBlank String name,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    int sortOrder,
    boolean breakSlot
) {
    public static PeriodDto from(TimetablePeriod p) {
        return new PeriodDto(p.getId(), p.getName(), p.getStartTime(), p.getEndTime(),
            p.getSortOrder(), p.isBreakSlot());
    }
}
