package in.schoolapp.hr.repository;

import in.schoolapp.hr.entity.SalaryStructure;
import in.schoolapp.school.entity.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, UUID> {

    Optional<SalaryStructure> findByIdAndSchoolId(UUID id, UUID schoolId);

    /** Staff-specific override, if any. */
    Optional<SalaryStructure> findFirstBySchoolIdAndStaffIdAndActiveOrderByEffectiveFromDesc(
        UUID schoolId, UUID staffId, boolean active);

    /** Role default — used as fallback when no staff override exists. */
    Optional<SalaryStructure> findFirstBySchoolIdAndRoleAndStaffIdIsNullAndActiveOrderByEffectiveFromDesc(
        UUID schoolId, StaffRole role, boolean active);

    List<SalaryStructure> findBySchoolIdAndActiveOrderByRoleAscEffectiveFromDesc(UUID schoolId, boolean active);
}
