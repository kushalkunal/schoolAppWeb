package in.schoolapp.fee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Named fee component: "Tuition", "Transport", "Library", etc. Scoped per tenant so each school
 * can define its own nomenclature.
 */
@Entity
@Table(name = "fee_heads",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "name"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FeeHead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // ---------- Slice 15: GST + late-fee config ----------

    /** GST percentage applied at invoice-generation time. 0.00 = exempt. */
    @Column(name = "gst_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercent = BigDecimal.ZERO;

    /** Per-day late fee in paise. 0 = head has no late-fee policy. */
    @Column(name = "late_fee_paise_per_day", nullable = false)
    private long lateFeePaisePerDay = 0;

    /** Days after due_date during which no late fee is charged. */
    @Column(name = "late_fee_grace_days", nullable = false)
    private int lateFeeGraceDays = 0;

    /** Cap on cumulative late fee for one invoice. NULL = uncapped. */
    @Column(name = "late_fee_cap_paise")
    private Long lateFeeCapPaise;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
