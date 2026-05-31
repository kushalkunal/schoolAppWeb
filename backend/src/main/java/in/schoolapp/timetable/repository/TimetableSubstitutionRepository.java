package in.schoolapp.timetable.repository;

import in.schoolapp.timetable.entity.TimetableSubstitution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimetableSubstitutionRepository extends JpaRepository<TimetableSubstitution, UUID> {

    Optional<TimetableSubstitution> findBySectionIdAndPeriodIdAndDate(
        UUID sectionId, UUID periodId, LocalDate date);

    List<TimetableSubstitution> findBySubstituteTeacherIdAndDate(UUID substituteTeacherId, LocalDate date);

    List<TimetableSubstitution> findByAbsentTeacherIdAndDate(UUID absentTeacherId, LocalDate date);

    List<TimetableSubstitution> findByDateAndSchoolId(LocalDate date, UUID schoolId);

    List<TimetableSubstitution> findBySchoolIdAndDateBetweenOrderByDateDesc(
        UUID schoolId, LocalDate from, LocalDate to);
}
