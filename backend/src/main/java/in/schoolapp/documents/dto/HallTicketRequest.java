package in.schoolapp.documents.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record HallTicketRequest(
    @Size(max = 20) String seatNumber,
    /**
     * Optional per-subject schedule. Each row becomes one line on the hall ticket's
     * schedule table. Pass {@code null} or empty to omit the table entirely (handy for
     * exams that publish the schedule separately).
     */
    List<ScheduleRow> schedule
) {
    public record ScheduleRow(
        String date,
        String subject,
        String time,
        String duration
    ) {}
}
