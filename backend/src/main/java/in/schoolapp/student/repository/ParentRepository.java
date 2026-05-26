package in.schoolapp.student.repository;

import in.schoolapp.student.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentRepository extends JpaRepository<Parent, UUID> {

    Optional<Parent> findBySchoolIdAndPhone(UUID schoolId, String phone);

    /** Used by WhatsApp inbox routing: inbound message → identify parent → identify tenant. */
    List<Parent> findAllByPhone(String phone);
}
