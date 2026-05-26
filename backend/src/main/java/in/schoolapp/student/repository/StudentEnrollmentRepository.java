package in.schoolapp.student.repository;

import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, UUID> {

    Optional<StudentEnrollment> findByStudentIdAndAcademicYearId(UUID studentId, UUID academicYearId);

    List<StudentEnrollment> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<StudentEnrollment> findBySectionIdAndStatus(UUID sectionId, EnrollmentStatus status);

    long countBySectionIdAndStatus(UUID sectionId, EnrollmentStatus status);
}
