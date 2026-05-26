package in.schoolapp.visitor;

import in.schoolapp.visitor.entity.Visitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitorRepository extends JpaRepository<Visitor, UUID> {

    Optional<Visitor> findByIdAndSchoolId(UUID id, UUID schoolId);

    Page<Visitor> findBySchoolIdOrderByInAtDesc(UUID schoolId, Pageable page);

    /** Currently-on-premises visitors (no out_at recorded). */
    List<Visitor> findBySchoolIdAndOutAtIsNullOrderByInAtDesc(UUID schoolId);
}
