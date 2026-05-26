package in.schoolapp.timetable.dto;

import in.schoolapp.timetable.entity.TimetableEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TimetableEntryDto(
    UUID id,                              // null on create
    @NotNull UUID sectionId,
    @NotNull UUID periodId,
    @Min(1) @Max(7) int dayOfWeek,
    UUID subjectId,                       // null = free period
    UUID teacherId,
    String note
) {
    public static TimetableEntryDto from(TimetableEntry e) {
        return new TimetableEntryDto(e.getId(), e.getSectionId(), e.getPeriodId(),
            e.getDayOfWeek(), e.getSubjectId(), e.getTeacherId(), e.getNote());
    }
}
