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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single payment instance: the accounting truth. Multiple payments can exist for one invoice
 * (partial payments). Quick-collect flows (gap analysis §5.3 problem 1) may create a payment
 * with {@code invoice_id = null} — i.e. "received ₹X from student Y for term fees" without
 * requiring the admin to pre-configure invoice schedules.
 */
@Entity
@Table(name = "fee_payments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "receipt_number"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "fee_head_id")
    private UUID feeHeadId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 10)
    private PaymentMode paymentMode;

    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "receipt_pdf_url", columnDefinition = "TEXT")
    private String receiptPdfUrl;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "collected_by_id")
    private UUID collectedById;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    /**
     * Provider-neutral payment reference (Stripe checkout session id, Razorpay payment link id,
     * etc.). Populated by {@link in.schoolapp.payment.PaymentEventListener} when a webhook
     * reports PAID. Unique per installation — the BSP idempotency key so retried webhooks
     * cannot create duplicate payments.
     */
    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** True when created by the OCR migration flow from scanned paper receipts. */
    @Column(name = "is_historical", nullable = false)
    private boolean historical = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
