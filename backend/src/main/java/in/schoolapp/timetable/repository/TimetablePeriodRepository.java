package in.schoolapp.timetable.repository;

import in.schoolapp.timetable.entity.TimetablePeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetablePeriodRepository extends JpaRepository<TimetablePeriod, UUID> {
    List<TimetablePeriod> findBySchoolIdOrderBySortOrderAsc(UUID schoolId);
}
