package in.schoolapp.timetable.repository;

import in.schoolapp.timetable.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    List<Classroom> findBySchoolIdOrderByName(UUID schoolId);

    Optional<Classroom> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndName(UUID schoolId, String name);
}
