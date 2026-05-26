package in.schoolapp.hr.repository;

import in.schoolapp.hr.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    Optional<Payslip> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<Payslip> findBySchoolIdAndStaffIdOrderByPayPeriodYearDescPayPeriodMonthDescVersionDesc(
        UUID schoolId, UUID staffId);

    Optional<Payslip> findFirstBySchoolIdAndStaffIdAndPayPeriodYearAndPayPeriodMonthOrderByVersionDesc(
        UUID schoolId, UUID staffId, int year, int month);
}
