package in.schoolapp.communication;

import in.schoolapp.billing.usage.UsageMetric;
import in.schoolapp.billing.usage.UsageService;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.entity.NotificationLog;
import in.schoolapp.communication.entity.NotificationStatus;
import in.schoolapp.communication.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes and transitions rows in {@code notification_log}. Called by the auditing decorator
 * around every dispatch and by the WhatsApp webhook on delivery/read receipts.
 * <p>
 * Each mutating method runs in a {@code REQUIRES_NEW} transaction — a failure to log must not
 * roll back the caller's business transaction (webhook ack, fee-payment create, etc.). This is
 * also why failures are caught and logged rather than rethrown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLogger {

    private final NotificationLogRepository repository;
    /**
     * Optional dep — {@code @WebMvcTest} slices that exclude the billing module won't have a
     * UsageService bean. ObjectProvider lets us no-op the counter in those tests without a
     * conditional config class.
     */
    private final ObjectProvider<UsageService> usageServiceProvider;

    /**
     * Creates a log row in {@code QUEUED} state. Returns the row id so the caller can move it
     * through SENT/FAILED later. Returns {@link Optional#empty()} when the message carries no
     * audit context — the call-site opted out of persistence.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> recordQueued(WhatsAppMessage message) {
        WhatsAppMessage.Audit audit = message.audit();
        if (audit == null || audit.schoolId() == null) return Optional.empty();
        try {
            NotificationLog row = new NotificationLog();
            row.setSchoolId(audit.schoolId());
            row.setEventType(message.type().name());
            row.setRecipientPhone(message.toPhone());
            row.setRecipientName(audit.recipientName());
            row.setStudentId(audit.studentId());
            row.setParentId(audit.parentId());
            row.setMessageBody(message.body());
            row.setStatus(NotificationStatus.QUEUED);
            row = repository.save(row);
            // Slice 4: increment the per-tenant outbound-message counter for plan-limit
            // tracking. Best-effort — UsageService.increment swallows internally so a counter
            // failure can't break a notification audit row.
            UsageService usage = usageServiceProvider.getIfAvailable();
            if (usage != null) {
                usage.increment(audit.schoolId(), UsageMetric.MESSAGES_SENT_MONTHLY, 1);
            }
            return Optional.of(row.getId());
        } catch (Exception e) {
            log.error("notification_log recordQueued failed — {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Marks a previously-queued row as sent and (if the provider returned one) stores the
     * {@code wa_message_id} so the webhook can find this row later.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID logId, String waMessageId) {
        if (logId == null) return;
        repository.findById(logId).ifPresent(row -> {
            row.setStatus(NotificationStatus.SENT);
            row.setSentAt(OffsetDateTime.now());
            if (waMessageId != null) row.setWaMessageId(waMessageId);
            repository.save(row);
        });
    }

    /** Webhook path: BSP says delivered. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDelivered(String waMessageId, OffsetDateTime at) {
        if (waMessageId == null) return;
        repository.findByWaMessageId(waMessageId).ifPresent(row -> {
            row.setStatus(NotificationStatus.DELIVERED);
            row.setDeliveredAt(at != null ? at : OffsetDateTime.now());
            repository.save(row);
        });
    }

    /** Webhook path: BSP says the recipient opened the message. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRead(String waMessageId, OffsetDateTime at) {
        if (waMessageId == null) return;
        repository.findByWaMessageId(waMessageId).ifPresent(row -> {
            row.setStatus(NotificationStatus.READ);
            row.setReadAt(at != null ? at : OffsetDateTime.now());
            repository.save(row);
        });
    }

    /**
     * Webhook-path failure OR dispatch-time failure. {@code waMessageId} is null when called
     * from dispatch; a non-null id looks up the row and transitions it to FAILED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID logId, String errorMessage) {
        if (logId == null) return;
        repository.findById(logId).ifPresent(row -> {
            row.setStatus(NotificationStatus.FAILED);
            row.setErrorMessage(truncate(errorMessage));
            repository.save(row);
        });
    }

    /** Webhook-side: failure reported by the BSP after we'd already handed the message off. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedByWaMessageId(String waMessageId, String errorMessage) {
        if (waMessageId == null) return;
        repository.findByWaMessageId(waMessageId).ifPresent(row -> {
            row.setStatus(NotificationStatus.FAILED);
            row.setErrorMessage(truncate(errorMessage));
            repository.save(row);
        });
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
