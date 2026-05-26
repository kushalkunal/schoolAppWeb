package in.schoolapp.fee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An amount owed by a student for a given fee head. Stored entirely in paise (integer) to avoid
 * floating-point drift — controllers convert to ₹ only at the edge.
 * <p>
 * Invoices support partial payment as a first-class concept (gap analysis §5.3 problem 3):
 * {@code amount_paid_paise} accumulates across multiple {@link FeePayment} rows until it equals
 * {@code amount_due_paise}. {@link #balancePaise()} derives the running balance.
 * <p>
 * Opening balances (for schools migrating from paper ledgers) are invoices with
 * {@code is_opening_balance = true}, no due date, no fee head — a single "carried forward"
 * amount per student.
 */
@Entity
@Table(name = "fee_invoices")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FeeInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "fee_head_id")
    private UUID feeHeadId;

    @Column(name = "amount_due_paise", nullable = false)
    private long amountDuePaise;

    @Column(name = "amount_paid_paise", nullable = false)
    private long amountPaidPaise = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "is_opening_balance", nullable = false)
    private boolean openingBalance = false;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ---------- Slice 15 additions ----------

    /** Sum of all LATE_FEE adjustments — kept here for fast defaulters-list rendering. */
    @Column(name = "late_fee_applied_paise", nullable = false)
    private long lateFeeAppliedPaise = 0;

    /** When the last late-fee tick fired. The cron uses this to compute days elapsed. */
    @Column(name = "last_late_fee_applied_at")
    private OffsetDateTime lastLateFeeAppliedAt;

    /** Snapshot of discount applied at invoice creation — reduces amount_due display. */
    @Column(name = "discount_applied_paise", nullable = false)
    private long discountAppliedPaise = 0;

    /** GST component of amount_due_paise. Receipt template shows this on a separate line. */
    @Column(name = "gst_paise", nullable = false)
    private long gstPaise = 0;

    /** Set when this invoice was split into installments — status becomes SUPERSEDED. */
    @Column(name = "superseded_by_plan_id")
    private UUID supersededByPlanId;

    // ---------- Slice 31: links back to the structure that generated this invoice ----------

    /** Set when the bulk invoice generator created this row from a fee structure version. */
    @Column(name = "structure_version_id", updatable = false)
    private UUID structureVersionId;

    /** Term number this invoice belongs to within {@link #structureVersionId}. */
    @Column(name = "structure_term_number", updatable = false)
    private Integer structureTermNumber;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public long balancePaise() {
        return amountDuePaise - amountPaidPaise;
    }

    /** Applies a payment and updates {@link #status} accordingly. Does not persist. */
    public void applyPayment(long amountPaise) {
        if (amountPaise <= 0) return;
        this.amountPaidPaise += amountPaise;
        if (amountPaidPaise >= amountDuePaise) {
            this.status = InvoiceStatus.PAID;
        } else {
            this.status = InvoiceStatus.PARTIAL;
        }
    }
}
