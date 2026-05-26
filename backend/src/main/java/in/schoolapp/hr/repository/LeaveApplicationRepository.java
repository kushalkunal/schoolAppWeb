package in.schoolapp.hr.repository;

import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.LeaveApplication.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, UUID> {

    Optional<LeaveApplication> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<LeaveApplication> findBySchoolIdAndStaffIdOrderByStartDateDesc(UUID schoolId, UUID staffId);

    List<LeaveApplication> findBySchoolIdAndStatusOrderByCreatedAtDesc(UUID schoolId, LeaveStatus status);

    /**
     * Approved leaves overlapping a given date range — payroll service joins this to
     * compute paid/unpaid leave days during a pay period.
     */
    List<LeaveApplication> findBySchoolIdAndStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        UUID schoolId, UUID staffId, LeaveStatus status, LocalDate to, LocalDate from);
}
