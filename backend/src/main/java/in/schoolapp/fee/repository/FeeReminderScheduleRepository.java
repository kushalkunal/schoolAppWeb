package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeReminderSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeReminderScheduleRepository extends JpaRepository<FeeReminderSchedule, UUID> {

    Optional<FeeReminderSchedule> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<FeeReminderSchedule> findBySchoolIdOrderByCreatedAtAsc(UUID schoolId);

    List<FeeReminderSchedule> findBySchoolIdAndActiveTrue(UUID schoolId);
}
