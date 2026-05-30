package in.schoolapp.timetable.dto;

import in.schoolapp.timetable.entity.TimetableSubstitution;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record SubstitutionDto(
    UUID id,
    @NotNull UUID sectionId,
    @NotNull UUID periodId,
    @NotNull LocalDate date,
    UUID absentTeacherId,
    @NotNull UUID substituteTeacherId,
    String reason
) {
    public static SubstitutionDto from(TimetableSubstitution s) {
        return new SubstitutionDto(s.getId(), s.getSectionId(), s.getPeriodId(),
            s.getDate(), s.getAbsentTeacherId(), s.getSubstituteTeacherId(), s.getReason());
    }
}
