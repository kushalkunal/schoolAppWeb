package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    Optional<Exam> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<Exam> findBySchoolIdAndAcademicYearIdOrderByStartDateDesc(
        UUID schoolId, UUID academicYearId);
}
