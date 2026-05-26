package in.schoolapp.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;

/**
 * Teacher submits a single day's attendance for a section. Follows the LLD "reverse marking"
 * model — only non-PRESENT entries need to be listed; students omitted from {@code entries} are
 * implicitly marked PRESENT.
 */
public record SubmitAttendanceRequest(
    @NotNull @PastOrPresent LocalDate date,
    @NotNull @Valid List<AttendanceEntryDto> entries
) {}
