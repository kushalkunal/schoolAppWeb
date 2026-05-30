package in.schoolapp.hr;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.hr.entity.Payslip;
import in.schoolapp.hr.entity.SalaryStructure;
import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import in.schoolapp.hr.repository.LeaveApplicationRepository;
import in.schoolapp.hr.repository.PayslipRepository;
import in.schoolapp.hr.repository.SalaryStructureRepository;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers payroll integrity (audit #11): no payslip for inactive staff, and only approved
 * attendance counts toward working days.
 */
@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock SalaryStructureRepository structureRepository;
    @Mock StaffAttendanceRepository attendanceRepository;
    @Mock LeaveApplicationRepository leaveRepository;
    @Mock PayslipRepository payslipRepository;
    @Mock StaffRepository staffRepository;
    PayrollService service;

    final UUID tenant = UUID.randomUUID();
    final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollService(
            structureRepository, attendanceRepository, leaveRepository, payslipRepository, staffRepository);
    }

    @Test
    void refusesPayslipForInactiveStaff() {
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff(false)));

        assertThatThrownBy(() -> service.generate(tenant, staffId, 2026, 6))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void onlyApprovedAttendanceCountsTowardWorkingDays() {
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff(true)));
        when(structureRepository
            .findFirstBySchoolIdAndStaffIdAndActiveOrderByEffectiveFromDesc(tenant, staffId, true))
            .thenReturn(Optional.of(structure()));
        // 3 approved present days + 2 unapproved present days -> only 3 should count.
        when(attendanceRepository.findStaffRange(eq(tenant), eq(staffId), any(), any()))
            .thenReturn(List.of(
                attendance(true), attendance(true), attendance(true),
                attendance(false), attendance(false)));
        when(leaveRepository
            .findBySchoolIdAndStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(payslipRepository
            .findFirstBySchoolIdAndStaffIdAndPayPeriodYearAndPayPeriodMonthOrderByVersionDesc(
                tenant, staffId, 2026, 6))
            .thenReturn(Optional.empty());
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(i -> i.getArgument(0));

        service.generate(tenant, staffId, 2026, 6);

        ArgumentCaptor<Payslip> saved = ArgumentCaptor.forClass(Payslip.class);
        verify(payslipRepository).save(saved.capture());
        assertThat(saved.getValue().getWorkingDays()).isEqualByComparingTo("3");
    }

    private Staff staff(boolean active) {
        Staff s = new Staff();
        s.setSchoolId(tenant);
        s.setRole(StaffRole.SUBJECT_TEACHER);
        s.setActive(active);
        return s;
    }

    private SalaryStructure structure() {
        SalaryStructure s = new SalaryStructure();
        s.setId(UUID.randomUUID());
        s.setSchoolId(tenant);
        s.setBasicPaise(1_000_000);   // ₹10,000 basic; other components default 0
        return s;
    }

    private StaffAttendance attendance(boolean approved) {
        StaffAttendance a = new StaffAttendance();
        a.setSchoolId(tenant);
        a.setStaffId(staffId);
        a.setAttendanceDate(LocalDate.of(2026, 6, 1));
        a.setStatus(StaffAttendanceStatus.PRESENT);
        a.setApproved(approved);
        return a;
    }
}
