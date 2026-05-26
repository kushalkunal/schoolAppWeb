package in.schoolapp.fee.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkReminderRequest(
    @NotEmpty List<UUID> studentIds,
    String messageOverride
) {}
