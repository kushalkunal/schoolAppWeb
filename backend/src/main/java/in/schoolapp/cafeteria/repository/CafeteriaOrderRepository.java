package in.schoolapp.cafeteria.repository;

import in.schoolapp.cafeteria.entity.CafeteriaOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CafeteriaOrderRepository extends JpaRepository<CafeteriaOrder, UUID> {
    Optional<CafeteriaOrder> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<CafeteriaOrder> findBySchoolIdAndStudentIdOrderByPlacedAtDesc(UUID schoolId, UUID studentId);
    List<CafeteriaOrder> findBySchoolIdOrderByPlacedAtDesc(UUID schoolId);
}
