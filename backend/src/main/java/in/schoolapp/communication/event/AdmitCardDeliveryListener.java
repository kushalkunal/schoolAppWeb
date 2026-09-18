package in.schoolapp.communication.event;

import in.schoolapp.academics.event.AdmitCardReadyEvent;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.parent.ParentNotificationService;
import in.schoolapp.feature.FeatureKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifies the primary parent that a student's admit card is ready (WhatsApp / email / SMS via
 * {@link ParentNotificationService}). The hall ticket PDF carries the exam schedule, so this is
 * also the exam-schedule touchpoint for parents. Fires AFTER_COMMIT so a rolled-back generation
 * never leaks messages; runs async off the request thread (important for bulk generation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmitCardDeliveryListener {

    private final ParentNotificationService parentNotificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdmitCardReady(AdmitCardReadyEvent event) {
        try {
            String exam = event.examName() == null ? "the exam" : event.examName();
            String body = String.format(
                "🎫 *Admit card ready — %s*\n\nYour child's admit card is now available. "
                    + "Download / print it from the link below; the exam schedule is printed on the card.\n\n%s",
                exam,
                event.pdfUrl() == null ? "" : event.pdfUrl());
            parentNotificationService.notify(
                event.tenantId(), event.studentId(),
                FeatureKey.PARENT_NOTIFY_EXAM_SCHEDULE,
                "Admit card ready — " + exam,
                body, event.pdfUrl(), MessageType.CIRCULAR);
        } catch (Exception e) {
            log.error("Admit card parent notification failed student={} — {}",
                event.studentId(), e.getMessage());
        }
    }
}
