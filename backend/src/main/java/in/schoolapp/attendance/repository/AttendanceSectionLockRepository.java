package in.schoolapp.attendance.repository;

import in.schoolapp.attendance.entity.AttendanceSectionLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSectionLockRepository extends JpaRepository<AttendanceSectionLock, UUID> {

    Optional<AttendanceSectionLock> findBySectionIdAndDate(UUID sectionId, LocalDate date);

    boolean existsBySectionIdAndDate(UUID sectionId, LocalDate date);
}
