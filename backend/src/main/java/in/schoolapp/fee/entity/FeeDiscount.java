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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Structural concession for a student. {@link FeeInvoiceService} consults this table at
 * invoice-generation time; the actual amount discounted is snapshotted into
 * {@code FeeInvoice.discountAppliedPaise} so retro-active edits to discount rates don't
 * silently mutate historic invoices.
 *
 * <p>Either {@link #percent} (e.g. 10.00 = 10%) OR {@link #fixedPaise} is set, never both.
 * A DB constraint enforces this.
 */
@Entity
@Table(name = "fee_discounts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FeeDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** NULL = applies to every fee head for this student (blanket discount). */
    @Column(name = "fee_head_id")
    private UUID feeHeadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(name = "fixed_paise")
    private Long fixedPaise;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** NULL = open-ended (e.g. lifetime sibling discount). */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    public enum DiscountType { SIBLING, SCHOLARSHIP, FINANCIAL_AID, STAFF_KID, EARLY_BIRD, CUSTOM }
}
