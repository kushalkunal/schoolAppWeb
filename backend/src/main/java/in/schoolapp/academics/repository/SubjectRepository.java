package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findBySchoolIdAndName(UUID schoolId, String name);

    boolean existsBySchoolIdAndName(UUID schoolId, String name);

    List<Subject> findBySchoolIdOrderByName(UUID schoolId);
}
