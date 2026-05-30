package in.schoolapp.student.repository;

import in.schoolapp.student.entity.StudentLeaveApplication;
import in.schoolapp.student.entity.StudentLeaveApplication.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentLeaveRepository extends JpaRepository<StudentLeaveApplication, UUID> {

    Optional<StudentLeaveApplication> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<StudentLeaveApplication> findByStudentIdOrderByStartDateDesc(UUID studentId);

    List<StudentLeaveApplication> findBySectionIdAndStatus(UUID sectionId, LeaveStatus status);

    List<StudentLeaveApplication> findBySchoolIdAndStatusOrderByCreatedAtDesc(UUID schoolId, LeaveStatus status);
}
