package in.schoolapp.fee;

import in.schoolapp.fee.entity.FeeHead;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GstCalculatorTest {

    @Test
    void zeroGstPassesThrough() {
        FeeHead h = new FeeHead();
        h.setGstPercent(BigDecimal.ZERO);
        var r = GstCalculator.apply(100_00L, h);
        assertThat(r.grossPaise()).isEqualTo(100_00L);
        assertThat(r.gstPaise()).isZero();
    }

    @Test
    void eighteenPercentOn1000Gives180() {
        FeeHead h = new FeeHead();
        h.setGstPercent(new BigDecimal("18.00"));
        var r = GstCalculator.apply(1000_00L, h);
        assertThat(r.gstPaise()).isEqualTo(180_00L);
        assertThat(r.grossPaise()).isEqualTo(1180_00L);
    }

    @Test
    void fractionalRoundsHalfEven() {
        // 18% of 105 paise = 18.9 paise → rounds to 19 (half-even)
        FeeHead h = new FeeHead();
        h.setGstPercent(new BigDecimal("18.00"));
        var r = GstCalculator.apply(105L, h);
        assertThat(r.gstPaise()).isEqualTo(19L);
    }

    @Test
    void nullHeadIsZero() {
        var r = GstCalculator.apply(100L, null);
        assertThat(r.grossPaise()).isEqualTo(100L);
        assertThat(r.gstPaise()).isZero();
    }
}
