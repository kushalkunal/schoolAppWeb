package in.schoolapp.ptm;

import in.schoolapp.ptm.entity.PtmSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PtmSlotRepository extends JpaRepository<PtmSlot, UUID> {
    Optional<PtmSlot> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<PtmSlot> findBySchoolIdAndSlotDateOrderByStartTime(UUID schoolId, LocalDate date);
    List<PtmSlot> findByTeacherIdAndSlotDateOrderByStartTime(UUID teacherId, LocalDate date);
}
