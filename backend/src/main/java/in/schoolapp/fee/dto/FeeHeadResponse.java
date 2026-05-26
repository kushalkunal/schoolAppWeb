package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.FeeHead;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Slice 15: exposes per-head GST + late-fee policy alongside the basic fields. Adding
 * fields here is safe; existing clients ignore unknown JSON keys (Jackson configured
 * with {@code FAIL_ON_UNKNOWN_PROPERTIES = false} app-wide).
 */
public record FeeHeadResponse(
    UUID id,
    String name,
    boolean active,
    BigDecimal gstPercent,
    long lateFeePaisePerDay,
    int lateFeeGraceDays,
    Long lateFeeCapPaise,
    OffsetDateTime createdAt
) {
    public static FeeHeadResponse from(FeeHead f) {
        return new FeeHeadResponse(
            f.getId(),
            f.getName(),
            f.isActive(),
            f.getGstPercent(),
            f.getLateFeePaisePerDay(),
            f.getLateFeeGraceDays(),
            f.getLateFeeCapPaise(),
            f.getCreatedAt()
        );
    }
}
