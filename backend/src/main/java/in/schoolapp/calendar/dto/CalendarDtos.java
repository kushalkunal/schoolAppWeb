package in.schoolapp.calendar.dto;

import in.schoolapp.calendar.entity.SchoolHoliday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for the per-tenant school calendar (working days + holidays). */
public final class CalendarDtos {
    private CalendarDtos() {}

    /** ISO day numbers (1=Mon … 7=Sun) that are working days. */
    public record CalendarResponse(List<Integer> workingDays, List<HolidayResponse> holidays) {}

    public record HolidayResponse(UUID id, LocalDate date, String name, String type) {
        public static HolidayResponse from(SchoolHoliday h) {
            return new HolidayResponse(h.getId(), h.getHolidayDate(), h.getName(), h.getType());
        }
    }

    public record WorkingDaysRequest(@NotNull List<Integer> days) {}

    public record CreateHolidayRequest(
        @NotNull LocalDate date,
        @NotBlank String name,
        String type
    ) {}
}
