package in.schoolapp.fee;

import in.schoolapp.fee.entity.FeeHead;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiny pure-function helper for invoice generation. Keeps GST math in one place so the
 * dashboard, invoice creator and receipt template all agree on rounding behaviour.
 *
 * <p>Rounding: bankers' rounding (HALF_EVEN) at the paise. The Indian GST regulation lets
 * either party choose any deterministic rule; HALF_EVEN matches GSTN's official rounding
 * recommendation since 2022.
 */
public final class GstCalculator {

    private GstCalculator() {}

    /**
     * Given a pre-tax amount and a fee head, compute the gross (base + GST) and GST
     * component separately. {@link FeeHead#getGstPercent()} of zero short-circuits to
     * {@code base, 0}.
     */
    public static GstBreakdown apply(long basePaise, FeeHead head) {
        BigDecimal pct = head != null && head.getGstPercent() != null
            ? head.getGstPercent() : BigDecimal.ZERO;
        if (pct.signum() == 0) return new GstBreakdown(basePaise, 0L);

        BigDecimal gst = BigDecimal.valueOf(basePaise)
            .multiply(pct)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_EVEN);
        long gstPaise = gst.longValueExact();
        return new GstBreakdown(basePaise + gstPaise, gstPaise);
    }

    public record GstBreakdown(long grossPaise, long gstPaise) {}
}
