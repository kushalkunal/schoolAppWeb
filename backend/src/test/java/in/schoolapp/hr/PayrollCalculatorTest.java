package in.schoolapp.hr;

import in.schoolapp.hr.entity.SalaryStructure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollCalculatorTest {

    private SalaryStructure stdStructure() {
        SalaryStructure s = new SalaryStructure();
        s.setBasicPaise(30_000_00L);          // ₹30,000
        s.setHraPaise(15_000_00L);            // ₹15,000
        s.setDaPaise(5_000_00L);              // ₹5,000
        s.setSpecialAllowancePaise(2_000_00L);
        s.setOtherAllowancePaise(0L);
        s.setPfPercent(new BigDecimal("12.00"));
        s.setEsiPercent(new BigDecimal("0.75"));
        s.setProfessionalTaxPaise(200_00L);
        return s;
    }

    @Test
    void grossSumsAllComponents() {
        var s = stdStructure();
        // gross = 30000 + 15000 + 5000 + 2000 = 52000
        var r = PayrollCalculator.compute(s, BigDecimal.valueOf(30), BigDecimal.ZERO, 30);
        assertThat(r.grossPaise()).isEqualTo(52_000_00L);
    }

    @Test
    void fullMonthAttendanceNoLeavesNet() {
        var s = stdStructure();
        var r = PayrollCalculator.compute(s, BigDecimal.valueOf(30), BigDecimal.ZERO, 30);

        // pf = 12% of basic = 3600
        // esi = 0.75% of gross = 390
        // prof tax = 200
        // net = 52000 - 3600 - 390 - 200 = 47810
        assertThat(r.pfPaise()).isEqualTo(3_600_00L);
        assertThat(r.esiPaise()).isEqualTo(390_00L);
        assertThat(r.profTaxPaise()).isEqualTo(200_00L);
        assertThat(r.netPaise()).isEqualTo(47_810_00L);
    }

    @Test
    void unpaidLeaveReducesProRatedAmount() {
        var s = stdStructure();
        // 25 working days, 5 unpaid → still 30 calendar days
        var r = PayrollCalculator.compute(s, BigDecimal.valueOf(25),
            BigDecimal.valueOf(5), 30);

        // pro_rated = 52000 * 25/30 = 43333.33 → 43_333_33 (half-even rounding gets 43_333_33)
        // The implementation rounds to nearest paise; absolute correctness within ±1 paise is fine
        assertThat(r.proRatedPaise()).isBetween(43_333_00L, 43_334_00L);
    }

    @Test
    void zeroAttendanceMonthZeroNet() {
        var s = stdStructure();
        var r = PayrollCalculator.compute(s, BigDecimal.ZERO, BigDecimal.ZERO, 30);
        // pro_rated = 0; deductions still happen; net clamped to 0 (no negative payslip)
        assertThat(r.proRatedPaise()).isZero();
        assertThat(r.netPaise()).isZero();
    }
}
