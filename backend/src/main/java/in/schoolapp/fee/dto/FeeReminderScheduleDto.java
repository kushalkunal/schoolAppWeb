package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.FeeReminderSchedule;
import in.schoolapp.fee.entity.ReminderTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FeeReminderScheduleDto(
    UUID id,
    @NotBlank @Size(max = 100) String name,
    @NotNull ReminderTriggerType triggerType,
    @PositiveOrZero int daysOffset,
    boolean includeUpiLink,
    boolean active
) {
    public static FeeReminderScheduleDto from(FeeReminderSchedule s) {
        return new FeeReminderScheduleDto(
            s.getId(), s.getName(), s.getTriggerType(), s.getDaysOffset(),
            s.isIncludeUpiLink(), s.isActive());
    }
}
