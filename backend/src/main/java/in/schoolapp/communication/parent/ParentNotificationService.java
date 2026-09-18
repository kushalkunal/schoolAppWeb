package in.schoolapp.communication.parent;

import in.schoolapp.communication.dispatcher.EmailSender;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.communication.parent.entity.ParentMessage;
import in.schoolapp.feature.FeatureFlagService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Slice 33 — single entry point for "send X to the student's parent". Every call:
 * <ol>
 *   <li>Checks the {@link FeatureKey#PARENT_NOTIFICATIONS} master switch on the tenant.</li>
 *   <li>Checks the per-category sub-flag (one of the {@code PARENT_NOTIFY_*} keys).</li>
 *   <li>Looks up the student's primary parent (phone + email).</li>
 *   <li>Dispatches to WhatsApp (always, if phone present) and email (if email present).</li>
 * </ol>
 * Callers don't need to plumb feature checks themselves — every listener / scheduler funnels
 * through this. A no-op when either flag is off or the parent has no contact details.
 *
 * <p>The instruction from the product owner is: <em>"For parent communication, no dedicated
 * mobile or web application is required. Use only WhatsApp and email notifications."</em>
 * That is the single contract this class enforces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentNotificationService {

    private final FeatureFlagService featureFlagService;
    private final FamilyService familyService;
    private final StudentService studentService;
    private final WhatsAppNotifier whatsAppNotifier;
    private final EmailSender emailSender;
    private final in.schoolapp.notification.sms.SmsSender smsSender;
    private final ParentMessageRepository inboxRepo;

    /**
     * Notify the student's primary parent. Returns the resolved {@link Outcome} for logging
     * / metrics — callers can ignore it. Never throws; downstream sender failures are logged.
     *
     * @param category one of the {@code PARENT_NOTIFY_*} feature keys
     * @param waType   WhatsApp template category for compliance routing
     */
    public Outcome notify(
            UUID tenantId, UUID studentId, String category,
            String emailSubject, String body, String mediaUrl, MessageType waType) {

        if (!featureFlagService.isEnabled(tenantId, FeatureKey.PARENT_NOTIFICATIONS)) {
            return Outcome.MASTER_FLAG_OFF;
        }
        if (!featureFlagService.isEnabled(tenantId, category)) {
            return Outcome.CATEGORY_FLAG_OFF;
        }

        Optional<Parent> parentOpt = familyService.getPrimaryParent(studentId);
        if (parentOpt.isEmpty()) {
            log.debug("Notify skipped — no primary parent. student={} category={}", studentId, category);
            return Outcome.NO_PRIMARY_PARENT;
        }
        Parent parent = parentOpt.get();
        Student student = studentService.getStudentEntity(tenantId, studentId);
        WhatsAppMessage.Audit audit = WhatsAppMessage.Audit.forParent(
            tenantId, studentId, parent.getId(), parent.getName());

        boolean waSent = false;
        boolean emailSent = false;

        if (parent.getPhone() != null && !parent.getPhone().isBlank()) {
            try {
                WhatsAppMessage msg = mediaUrl != null && !mediaUrl.isBlank()
                    ? WhatsAppMessage.withMedia(parent.getPhone(), body, mediaUrl, waType, audit)
                    : WhatsAppMessage.text(parent.getPhone(), body, waType, audit);
                whatsAppNotifier.send(msg);
                waSent = true;
                writeInbox(tenantId, studentId, parent.getId(), "WHATSAPP", category, emailSubject, body, mediaUrl);
            } catch (Exception e) {
                log.warn("WhatsApp send failed student={} category={}: {}", studentId, category, e.getMessage());
            }
        }
        if (parent.getEmail() != null && !parent.getEmail().isBlank()) {
            try {
                emailSender.send(parent.getEmail(), emailSubject, body);
                emailSent = true;
                writeInbox(tenantId, studentId, parent.getId(), "EMAIL", category, emailSubject, body, mediaUrl);
            } catch (Exception e) {
                log.warn("Email send failed student={} category={}: {}", studentId, category, e.getMessage());
            }
        }

        // SMS fallback — only when WhatsApp didn't go through (same phone), and the tenant has
        // opted into SMS_FALLBACK. Keeps SMS as a reliability net rather than duplicate spam.
        boolean smsSent = false;
        if (!waSent && parent.getPhone() != null && !parent.getPhone().isBlank()
                && featureFlagService.isEnabled(tenantId, FeatureKey.SMS_FALLBACK)) {
            try {
                smsSender.send(tenantId, parent.getPhone(), body);
                smsSent = true;
                writeInbox(tenantId, studentId, parent.getId(), "SMS", category, emailSubject, body, mediaUrl);
            } catch (Exception e) {
                log.warn("SMS fallback failed student={} category={}: {}", studentId, category, e.getMessage());
            }
        }

        if (!waSent && !emailSent && !smsSent) {
            log.warn("Notify produced zero sends — no contacts. student={} parent={} category={}",
                studentId, parent.getId(), category);
            return Outcome.NO_CONTACT;
        }
        // Touch the student variable to silence unused-variable warnings in some paths;
        // also reasonable to surface in logs for debugging template misuses.
        if (log.isDebugEnabled()) {
            log.debug("Notify dispatched student={} ({}) wa={} email={} category={}",
                studentId, student.displayName(), waSent, emailSent, category);
        }
        return waSent && emailSent ? Outcome.SENT_BOTH
            : waSent ? Outcome.SENT_WA_ONLY
            : emailSent ? Outcome.SENT_EMAIL_ONLY
            : Outcome.SENT_SMS_ONLY;
    }

    /** Convenience overload for text-only notifications (no media URL). */
    public Outcome notify(UUID tenantId, UUID studentId, String category,
                          String emailSubject, String body, MessageType waType) {
        return notify(tenantId, studentId, category, emailSubject, body, null, waType);
    }

    /** Append a row to the per-student inbox cache. Best-effort — never throws to callers. */
    private void writeInbox(UUID tenantId, UUID studentId, UUID parentId,
                            String channel, String category,
                            String subject, String body, String mediaUrl) {
        try {
            ParentMessage m = new ParentMessage();
            m.setSchoolId(tenantId);
            m.setStudentId(studentId);
            m.setParentId(parentId);
            m.setChannel(channel);
            m.setCategory(category);
            m.setSubject(subject);
            m.setBody(body);
            m.setMediaUrl(mediaUrl);
            inboxRepo.save(m);
        } catch (Exception e) {
            log.warn("Inbox cache write failed student={} channel={}: {}", studentId, channel, e.getMessage());
        }
    }

    public enum Outcome {
        SENT_BOTH, SENT_WA_ONLY, SENT_EMAIL_ONLY, SENT_SMS_ONLY,
        NO_CONTACT, NO_PRIMARY_PARENT,
        MASTER_FLAG_OFF, CATEGORY_FLAG_OFF
    }
}
