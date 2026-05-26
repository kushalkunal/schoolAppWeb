package in.schoolapp.communication;

import in.schoolapp.communication.dispatcher.RawWhatsAppNotifier;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The public {@link WhatsAppNotifier} bean — sits in front of the raw provider notifier
 * (logging, WATI, Interakt, …) and writes every dispatch attempt to {@code notification_log}
 * via {@link NotificationLogger}. Call-sites inject {@code WhatsAppNotifier} and automatically
 * get auditing; they don't have to know the logger exists.
 * <p>
 * Dispatch failures are swallowed here (logged only) — notifications are best-effort and must
 * never break the business transaction that triggered them (payment commit, attendance save).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditingWhatsAppNotifier implements WhatsAppNotifier {

    private final RawWhatsAppNotifier raw;
    private final NotificationLogger logger;

    @Override
    public void send(WhatsAppMessage message) {
        Optional<UUID> logIdOpt = logger.recordQueued(message);
        try {
            String waMessageId = raw.send(message);
            logIdOpt.ifPresent(id -> logger.markSent(id, waMessageId));
        } catch (Exception e) {
            log.error("WhatsApp dispatch failed — type={} phone-suffix-hash={} err={}",
                message.type(),
                message.toPhone() == null ? "?" : message.toPhone().hashCode(),
                e.getMessage());
            logIdOpt.ifPresent(id -> logger.markFailed(id, e.getMessage()));
        }
    }
}
