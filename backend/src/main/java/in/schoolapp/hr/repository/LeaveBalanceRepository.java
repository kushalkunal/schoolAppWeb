package in.schoolapp.hr.repository;

import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import in.schoolapp.hr.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findBySchoolIdAndStaffIdAndLeaveTypeAndYear(
        UUID schoolId, UUID staffId, LeaveType leaveType, int year);

    List<LeaveBalance> findBySchoolIdAndStaffIdAndYearOrderByLeaveType(
        UUID schoolId, UUID staffId, int year);
}
