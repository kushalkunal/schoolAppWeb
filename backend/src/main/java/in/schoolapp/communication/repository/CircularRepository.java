package in.schoolapp.communication.repository;

import in.schoolapp.communication.entity.Circular;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CircularRepository extends JpaRepository<Circular, UUID> {

    Optional<Circular> findByIdAndSchoolId(UUID id, UUID schoolId);

    Page<Circular> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId, Pageable pageable);
}
