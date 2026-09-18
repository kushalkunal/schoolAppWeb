package in.schoolapp.academics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request + response shapes for an exam's examination timetable. */
public final class ExamScheduleDtos {

    private ExamScheduleDtos() {}

    /** One sitting as returned to the client (subjectName resolved for display). */
    public record ScheduleRow(
        UUID subjectId,
        String subjectName,
        LocalDate examDate,
        LocalTime startTime,
        LocalTime endTime
    ) {}

    /** Replace-all upsert: the full set of sittings for the exam. */
    public record UpsertScheduleRequest(
        @NotNull @Valid List<Sitting> sittings
    ) {
        public record Sitting(
            @NotNull UUID subjectId,
            @NotNull LocalDate examDate,
            LocalTime startTime,
            LocalTime endTime
        ) {}
    }
}
