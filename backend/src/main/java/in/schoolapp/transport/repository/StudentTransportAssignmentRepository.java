package in.schoolapp.transport.repository;

import in.schoolapp.transport.entity.StudentTransportAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentTransportAssignmentRepository
    extends JpaRepository<StudentTransportAssignment, UUID> {

    List<StudentTransportAssignment> findByStudentIdAndEndDateIsNull(UUID studentId);
    List<StudentTransportAssignment> findByRouteIdAndEndDateIsNull(UUID routeId);
    List<StudentTransportAssignment> findBySchoolId(UUID schoolId);
}
