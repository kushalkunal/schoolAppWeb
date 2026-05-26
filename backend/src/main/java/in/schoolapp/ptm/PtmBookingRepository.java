package in.schoolapp.ptm;

import in.schoolapp.ptm.entity.PtmBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PtmBookingRepository extends JpaRepository<PtmBooking, UUID> {
    Optional<PtmBooking> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<PtmBooking> findBySlotIdOrderByCreatedAt(UUID slotId);
    List<PtmBooking> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
