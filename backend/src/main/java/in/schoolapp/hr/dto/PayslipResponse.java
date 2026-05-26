package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.Payslip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PayslipResponse(
    UUID id,
    UUID staffId,
    int year,
    int month,
    int version,
    BigDecimal workingDays,
    BigDecimal leaveDaysUnpaid,
    long grossPaise,
    long deductionsPaise,
    long netPaise,
    Map<String, Object> breakdown,
    String pdfUrl,
    OffsetDateTime generatedAt
) {
    public static PayslipResponse from(Payslip p) {
        return new PayslipResponse(
            p.getId(), p.getStaffId(),
            p.getPayPeriodYear(), p.getPayPeriodMonth(), p.getVersion(),
            p.getWorkingDays(), p.getLeaveDaysUnpaid(),
            p.getGrossPaise(), p.getDeductionsPaise(), p.getNetPaise(),
            p.getBreakdownJson(),
            p.getPdfUrl(),
            p.getGeneratedAt());
    }
}
