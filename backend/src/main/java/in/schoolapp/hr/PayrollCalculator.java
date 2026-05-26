package in.schoolapp.hr;

import in.schoolapp.hr.entity.SalaryStructure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure-function payroll math. Keeps the formula in one place so the service, the payslip
 * PDF template and unit tests all agree.
 *
 * <p>Indian standard (simplified — schools usually have a slightly more complex deck):
 * <pre>
 *   gross      = basic + hra + da + special_allowance + other_allowance
 *   pf         = basic × pf_percent / 100        (employee contribution)
 *   esi        = gross × esi_percent / 100       (if applicable, often skipped for higher earners)
 *   prof_tax   = state-defined flat amount
 *   pro_rated  = gross × (working_days / total_days)
 *   lwp_cut    = gross × (unpaid_leave_days / total_days)
 *   net        = pro_rated − pf − esi − prof_tax − lwp_cut
 * </pre>
 */
public final class PayrollCalculator {

    private PayrollCalculator() {}

    public static Result compute(SalaryStructure s,
                                 BigDecimal workingDays,
                                 BigDecimal unpaidLeaveDays,
                                 int totalDaysInMonth) {
        long gross = s.grossPaise();
        BigDecimal totalDays = BigDecimal.valueOf(totalDaysInMonth);

        // Pro-rate gross by attendance.
        long proRated = scaleByDays(gross, workingDays, totalDays);

        // Loss-of-pay deduction for unpaid leave days.
        long lwpCut = scaleByDays(gross, unpaidLeaveDays, totalDays);

        long pf = percentOf(s.getBasicPaise(), s.getPfPercent());
        long esi = percentOf(gross, s.getEsiPercent());
        long profTax = s.getProfessionalTaxPaise();

        long deductions = pf + esi + profTax + lwpCut;
        long net = proRated - pf - esi - profTax;

        return new Result(gross, proRated, lwpCut, pf, esi, profTax, deductions, Math.max(net, 0));
    }

    private static long scaleByDays(long amountPaise, BigDecimal days, BigDecimal totalDays) {
        if (totalDays.signum() == 0) return 0;
        return BigDecimal.valueOf(amountPaise)
            .multiply(days)
            .divide(totalDays, 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    }

    private static long percentOf(long amountPaise, BigDecimal percent) {
        if (percent == null || percent.signum() == 0) return 0;
        return BigDecimal.valueOf(amountPaise)
            .multiply(percent)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    }

    public record Result(
        long grossPaise,
        long proRatedPaise,
        long lwpCutPaise,
        long pfPaise,
        long esiPaise,
        long profTaxPaise,
        long deductionsPaise,
        long netPaise
    ) {}
}
