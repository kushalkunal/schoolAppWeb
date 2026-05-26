package in.schoolapp.school.repository;

import in.schoolapp.school.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    List<SchoolClass> findBySchoolIdOrderBySortOrderAscNameAsc(UUID schoolId);

    Optional<SchoolClass> findBySchoolIdAndName(UUID schoolId, String name);

    boolean existsBySchoolIdAndName(UUID schoolId, String name);
}
