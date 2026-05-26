package in.schoolapp.infirmary;

import in.schoolapp.infirmary.entity.InfirmaryVisit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InfirmaryVisitRepository extends JpaRepository<InfirmaryVisit, UUID> {
    Optional<InfirmaryVisit> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<InfirmaryVisit> findByStudentIdOrderByVisitedAtDesc(UUID studentId);
    Page<InfirmaryVisit> findBySchoolIdOrderByVisitedAtDesc(UUID schoolId, Pageable page);
}
