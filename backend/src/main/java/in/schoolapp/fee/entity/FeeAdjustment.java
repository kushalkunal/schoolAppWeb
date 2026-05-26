package in.schoolapp.fee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit ledger of every non-payment mutation that touches an invoice or
 * payment: applied late fees, applied discounts, refunds, write-offs, manual credits.
 *
 * <p>{@code amount_paise} sign convention:
 * <ul>
 *   <li>Positive → invoice balance INCREASED (late fee, manual debit)</li>
 *   <li>Negative → invoice balance DECREASED (discount applied, refund, write-off)</li>
 * </ul>
 *
 * Querying the sum gives the net adjustment; pairing with {@link FeePayment} gives a
 * complete ledger of a student's account.
 */
@Entity
@Table(name = "fee_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class FeeAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    /** Set when adjustment_type = REFUND — references the payment being reversed. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private AdjustmentType adjustmentType;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    public enum AdjustmentType {
        LATE_FEE,
        DISCOUNT_APPLIED,
        REFUND,
        WRITE_OFF,
        MANUAL_CREDIT,
        MANUAL_DEBIT
    }
}
