package in.schoolapp.incident;

import in.schoolapp.incident.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Optional<Incident> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<Incident> findByStudentIdOrderByOccurredOnDesc(UUID studentId);
    Page<Incident> findBySchoolIdOrderByOccurredOnDesc(UUID schoolId, Pageable page);
}
