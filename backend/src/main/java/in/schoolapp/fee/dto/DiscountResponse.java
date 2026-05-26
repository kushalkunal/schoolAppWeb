package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.FeeDiscount;
import in.schoolapp.fee.entity.FeeDiscount.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DiscountResponse(
    UUID id,
    UUID studentId,
    UUID feeHeadId,
    DiscountType discountType,
    BigDecimal percent,
    Long fixedPaise,
    LocalDate validFrom,
    LocalDate validUntil,
    String reason,
    boolean active
) {
    public static DiscountResponse from(FeeDiscount d) {
        return new DiscountResponse(
            d.getId(),
            d.getStudentId(),
            d.getFeeHeadId(),
            d.getDiscountType(),
            d.getPercent(),
            d.getFixedPaise(),
            d.getValidFrom(),
            d.getValidUntil(),
            d.getReason(),
            d.isActive()
        );
    }
}
