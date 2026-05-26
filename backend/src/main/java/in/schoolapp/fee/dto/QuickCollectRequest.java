package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.PaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Zero-config fee collection per gap analysis §5.3 problem 1: no fee structure, categories, or
 * schedules required up front. Admin types student + amount + mode, receipt goes out.
 * <p>
 * If {@code invoiceId} is provided the payment is applied to that specific invoice (partial
 * payments supported). Otherwise it's applied FIFO to the student's pending invoices, and any
 * excess is recorded as a standalone payment (no implicit invoice creation).
 */
public record QuickCollectRequest(
    @NotNull UUID studentId,
    @NotNull @Positive Long amountPaise,
    @NotNull PaymentMode paymentMode,
    UUID feeHeadId,
    UUID invoiceId,
    LocalDate paymentDate,
    @Size(max = 500) String notes
) {}
