package in.schoolapp.fee.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundRequest(
    /** Partial refund — leave null to refund the full payment. */
    @Positive Long amountPaise,
    @Size(max = 500) String reason
) {}
