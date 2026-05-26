package in.schoolapp.communication.event;

import in.schoolapp.communication.dispatcher.EmailSender;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.communication.template.MessageTemplateService;
import in.schoolapp.feature.FeatureFlagService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.fee.FeeInvoiceService;
import in.schoolapp.fee.event.FeePaymentCreatedEvent;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Sends the WhatsApp fee receipt after a payment is committed. Uses
 * {@link TransactionalEventListener} with {@code AFTER_COMMIT} so a rollback of the payment
 * transaction never leaves a receipt message in the wild — only committed payments trigger
 * notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptDeliveryListener {

    private final StudentService studentService;
    private final SchoolService schoolService;
    private final FamilyService familyService;
    private final FeeInvoiceService invoiceService;
    private final MessageTemplateService templates;
    private final WhatsAppNotifier whatsAppNotifier;
    private final EmailSender emailSender;
    private final FeatureFlagService featureFlagService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayment(FeePaymentCreatedEvent event) {
        try {
            if (!featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFICATIONS)
                || !featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFY_FEE_RECEIPT)) {
                return;
            }
            Student student = studentService.getStudentEntity(event.tenantId(), event.studentId());
            School school = schoolService.getSchoolEntity(event.tenantId());

            Optional<Parent> parentOpt = familyService.getPrimaryParent(event.studentId());
            if (parentOpt.isEmpty() || parentOpt.get().getPhone() == null) {
                log.warn("Receipt not sent — no primary parent phone for student={}", event.studentId());
                return;
            }
            Parent parent = parentOpt.get();

            long outstanding = invoiceService.getOutstanding(event.studentId());
            // Derive mode for template purely from the event — we don't re-read the payment row
            String body = templates.feeReceipt(
                school.getName(),
                student,
                event.amountPaise(),
                event.receiptNumber(),
                "—",  // payment mode not in event; Slice 5: include in event
                java.time.LocalDate.now(),
                outstanding
            );

            WhatsAppMessage.Audit audit = WhatsAppMessage.Audit.forParent(
                event.tenantId(), event.studentId(), parent.getId(), parent.getName());
            whatsAppNotifier.send(WhatsAppMessage.withMedia(
                parent.getPhone(), body, event.receiptPdfUrl(), MessageType.FEE_RECEIPT, audit));
            // Email mirror — parents who only check email get the PDF link too.
            if (parent.getEmail() != null && !parent.getEmail().isBlank()) {
                try {
                    String emailBody = body + "\n\nReceipt PDF: " + event.receiptPdfUrl();
                    emailSender.send(parent.getEmail(),
                        school.getName() + " — Fee receipt " + event.receiptNumber(), emailBody);
                } catch (Exception e) {
                    log.warn("Receipt email failed parent={} — {}", parent.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            // Never rethrow from a notification path — log and move on. A missed receipt
            // message is a degraded experience, not a data-integrity failure.
            log.error("Receipt delivery failed for payment={} — {}", event.paymentId(), e.getMessage());
        }
    }
}
