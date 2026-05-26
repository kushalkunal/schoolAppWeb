package in.schoolapp.school.repository;

import in.schoolapp.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findByPhone(String phone);

    Optional<School> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
