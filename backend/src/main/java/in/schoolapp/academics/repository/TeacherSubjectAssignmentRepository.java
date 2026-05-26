package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.TeacherSubjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherSubjectAssignmentRepository
        extends JpaRepository<TeacherSubjectAssignment, UUID> {

    Optional<TeacherSubjectAssignment> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<TeacherSubjectAssignment> findBySchoolIdAndAcademicYearId(UUID schoolId, UUID academicYearId);

    List<TeacherSubjectAssignment> findByStaffIdAndAcademicYearId(UUID staffId, UUID academicYearId);

    List<TeacherSubjectAssignment> findBySectionIdAndAcademicYearId(UUID sectionId, UUID academicYearId);

    /** Authorisation check: is this staff assigned to (section, subject) for the current year? */
    boolean existsByStaffIdAndSubjectIdAndSectionIdAndAcademicYearId(
        UUID staffId, UUID subjectId, UUID sectionId, UUID academicYearId);
}
