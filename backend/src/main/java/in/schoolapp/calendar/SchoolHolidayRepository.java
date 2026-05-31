package in.schoolapp.calendar;

import in.schoolapp.calendar.entity.SchoolHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolHolidayRepository extends JpaRepository<SchoolHoliday, UUID> {

    List<SchoolHoliday> findBySchoolIdOrderByHolidayDateAsc(UUID schoolId);

    List<SchoolHoliday> findBySchoolIdAndHolidayDateBetweenOrderByHolidayDateAsc(
        UUID schoolId, LocalDate from, LocalDate to);

    Optional<SchoolHoliday> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndHolidayDate(UUID schoolId, LocalDate holidayDate);
}
