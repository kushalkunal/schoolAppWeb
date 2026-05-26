package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.FeeDiscount.DiscountType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Either {@link #percent} or {@link #fixedPaise} is set, never both. The service rejects the
 * "both" / "neither" cases at runtime — bean-validation only ensures range constraints.
 */
public record CreateDiscountRequest(
    @NotNull UUID studentId,
    UUID feeHeadId,                                       // null = applies to all heads
    @NotNull DiscountType discountType,
    @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percent,
    @PositiveOrZero Long fixedPaise,
    LocalDate validFrom,                                  // null = today
    LocalDate validUntil,                                 // null = open-ended
    @Size(max = 500) String reason
) {}
