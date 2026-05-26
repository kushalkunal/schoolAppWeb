package in.schoolapp.school.repository;

import in.schoolapp.school.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    Optional<AcademicYear> findBySchoolIdAndCurrentTrue(UUID schoolId);

    List<AcademicYear> findBySchoolIdOrderByStartDateDesc(UUID schoolId);

    boolean existsBySchoolIdAndName(UUID schoolId, String name);
}
