package in.schoolapp.school.repository;

import in.schoolapp.school.entity.SubstituteAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubstituteAssignmentRepository extends JpaRepository<SubstituteAssignment, UUID> {

    Optional<SubstituteAssignment> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<SubstituteAssignment> findBySchoolIdAndAssignedDateOrderByCreatedAtAsc(
        UUID schoolId, LocalDate date);

    List<SubstituteAssignment> findBySchoolIdAndAssignedDateBetweenOrderByAssignedDateAscCreatedAtAsc(
        UUID schoolId, LocalDate from, LocalDate to);

    List<SubstituteAssignment> findBySubstituteIdAndAssignedDateOrderByCreatedAtAsc(
        UUID substituteId, LocalDate date);

    Optional<SubstituteAssignment> findBySchoolIdAndSectionIdAndAssignedDate(
        UUID schoolId, UUID sectionId, LocalDate date);
}
