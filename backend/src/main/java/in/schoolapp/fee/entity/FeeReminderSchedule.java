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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single reminder-schedule rule for a tenant. Mapped to {@code fee_reminder_schedules} from
 * V1. Multiple schedules per tenant form an escalation ladder (5 before, on-due, 3 after,
 * 7 after, 14 after). The cron {@link in.schoolapp.fee.FeeReminderSchedulerService} applies
 * each active schedule daily and fires WhatsApp reminders for matching invoices.
 */
@Entity
@Table(name = "fee_reminder_schedules",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "trigger_type", "days_offset"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FeeReminderSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private ReminderTriggerType triggerType;

    /** Always non-negative; interpretation depends on {@link #triggerType}. */
    @Column(name = "days_offset", nullable = false)
    private int daysOffset;

    @Column(name = "include_upi_link", nullable = false)
    private boolean includeUpiLink = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
