package in.schoolapp.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit trail of subscription state transitions. No FK to subscriptions(id) so
 * a final CANCELLED row survives a future hard-delete of the row it referred to.
 *
 * <p>event_type values: {@code TRIAL_STARTED | PLAN_CHANGED | SUSPENDED | RESUMED |
 * CANCELLED | TRIAL_EXPIRED | PAYMENT_FAILED}.
 */
@Entity
@Table(name = "subscription_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_plan_code", length = 40)
    private String fromPlanCode;

    @Column(name = "to_plan_code", length = 40)
    private String toPlanCode;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "actor_staff_id")
    private UUID actorStaffId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
