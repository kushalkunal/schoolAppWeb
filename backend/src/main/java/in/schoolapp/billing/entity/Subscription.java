package in.schoolapp.billing.entity;

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
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Current subscription state for a school. One row per school (enforced by unique constraint).
 * <p>
 * Not extending {@code BaseEntity} on purpose: the platform-admin endpoints need to read/write
 * this row outside any tenant context, and {@code BaseEntity}'s {@code schoolId} not-null +
 * not-updatable already gives us tenant binding here — we just keep the field explicit so
 * platform code can construct/update without needing to be inside a TenantContext.
 */
@Entity
@Table(name = "subscriptions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false, unique = true)
    private UUID schoolId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(name = "current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "external_customer_id", length = 120)
    private String externalCustomerId;

    @Column(name = "external_subscription_id", length = 120)
    private String externalSubscriptionId;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
