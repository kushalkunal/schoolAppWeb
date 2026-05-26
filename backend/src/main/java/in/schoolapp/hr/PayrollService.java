package in.schoolapp.hr;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.hr.dto.PayslipResponse;
import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.Payslip;
import in.schoolapp.hr.entity.SalaryStructure;
import in.schoolapp.hr.repository.LeaveApplicationRepository;
import in.schoolapp.hr.repository.PayslipRepository;
import in.schoolapp.hr.repository.SalaryStructureRepository;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates payslips for {@code (staff, year, month)}. Workflow:
 *
 * <ol>
 *   <li>Resolve the salary structure (staff override > role default).</li>
 *   <li>Compute working days from {@link StaffAttendanceRepository}; defaults to total
 *       calendar days if no attendance has been marked (so a freshly onboarded school can
 *       still generate payslips before configuring attendance).</li>
 *   <li>Subtract approved UNPAID leave days from working days.</li>
 *   <li>Run {@link PayrollCalculator}.</li>
 *   <li>Persist an immutable {@link Payslip} row with the full breakdown in JSONB.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final SalaryStructureRepository structureRepository;
    private final StaffAttendanceRepository attendanceRepository;
    private final LeaveApplicationRepository leaveRepository;
    private final PayslipRepository payslipRepository;
    private final StaffRepository staffRepository;

    @Transactional
    public PayslipResponse generate(UUID tenantId, UUID staffId, int year, int month) {
        Staff staff = staffRepository.findById(staffId)
            .filter(s -> s.getSchoolId().equals(tenantId))
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Staff not found"));

        SalaryStructure structure = structureRepository
            .findFirstBySchoolIdAndStaffIdAndActiveOrderByEffectiveFromDesc(tenantId, staffId, true)
            .orElseGet(() -> structureRepository
                .findFirstBySchoolIdAndRoleAndStaffIdIsNullAndActiveOrderByEffectiveFromDesc(
                    tenantId, staff.getRole(), true)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                    "No salary structure for staff " + staffId + " or role " + staff.getRole())));

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        int totalDays = from.lengthOfMonth();

        // Working days from attendance — default to full month when no rows exist yet.
        var rows = attendanceRepository.findStaffRange(tenantId, staffId, from, to);
        BigDecimal workingDays;
        if (rows.isEmpty()) {
            workingDays = BigDecimal.valueOf(totalDays);
        } else {
            double counted = rows.stream().mapToDouble(r -> switch (r.getStatus()) {
                case PRESENT, LATE, LEAVE -> 1.0;   // LEAVE here = approved paid leave
                case HALF_DAY -> 0.5;
                case ABSENT, HOLIDAY -> 0.0;
            }).sum();
            workingDays = BigDecimal.valueOf(counted);
        }

        // Unpaid leave days approved during this month.
        var unpaidLeaves = leaveRepository
            .findBySchoolIdAndStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                tenantId, staffId,
                LeaveApplication.LeaveStatus.APPROVED,
                to, from)
            .stream()
            .filter(la -> la.getLeaveType() == LeaveApplication.LeaveType.UNPAID)
            .toList();
        BigDecimal unpaidDays = unpaidLeaves.stream()
            .map(LeaveApplication::getDays)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var calc = PayrollCalculator.compute(structure, workingDays, unpaidDays, totalDays);

        // Determine next version (re-issues increment).
        int version = payslipRepository
            .findFirstBySchoolIdAndStaffIdAndPayPeriodYearAndPayPeriodMonthOrderByVersionDesc(
                tenantId, staffId, year, month)
            .map(p -> p.getVersion() + 1)
            .orElse(1);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("structureId", structure.getId().toString());
        breakdown.put("basic", structure.getBasicPaise());
        breakdown.put("hra", structure.getHraPaise());
        breakdown.put("da", structure.getDaPaise());
        breakdown.put("specialAllowance", structure.getSpecialAllowancePaise());
        breakdown.put("otherAllowance", structure.getOtherAllowancePaise());
        breakdown.put("gross", calc.grossPaise());
        breakdown.put("proRated", calc.proRatedPaise());
        breakdown.put("lwpCut", calc.lwpCutPaise());
        breakdown.put("pf", calc.pfPaise());
        breakdown.put("esi", calc.esiPaise());
        breakdown.put("profTax", calc.profTaxPaise());
        breakdown.put("net", calc.netPaise());
        breakdown.put("workingDays", workingDays);
        breakdown.put("unpaidLeaveDays", unpaidDays);
        breakdown.put("totalDaysInMonth", totalDays);

        Payslip slip = new Payslip();
        slip.setSchoolId(tenantId);
        slip.setStaffId(staffId);
        slip.setPayPeriodYear(year);
        slip.setPayPeriodMonth(month);
        slip.setVersion(version);
        slip.setWorkingDays(workingDays);
        slip.setLeaveDaysUnpaid(unpaidDays);
        slip.setGrossPaise(calc.grossPaise());
        slip.setDeductionsPaise(calc.deductionsPaise());
        slip.setNetPaise(calc.netPaise());
        slip.setBreakdownJson(breakdown);
        slip = payslipRepository.save(slip);

        log.info("Payslip generated tenant={} staff={} {}/{} v{} net={}",
            tenantId, staffId, month, year, version, calc.netPaise());
        return PayslipResponse.from(slip);
    }

    public List<PayslipResponse> listForStaff(UUID tenantId, UUID staffId) {
        return payslipRepository
            .findBySchoolIdAndStaffIdOrderByPayPeriodYearDescPayPeriodMonthDescVersionDesc(tenantId, staffId)
            .stream().map(PayslipResponse::from).toList();
    }
}
