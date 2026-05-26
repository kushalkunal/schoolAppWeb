package in.schoolapp.timetable.repository;

import in.schoolapp.timetable.entity.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {

    List<TimetableEntry> findBySectionIdOrderByDayOfWeekAsc(UUID sectionId);

    List<TimetableEntry> findByTeacherIdAndDayOfWeek(UUID teacherId, int dayOfWeek);

    List<TimetableEntry> findByTeacherId(UUID teacherId);
}
