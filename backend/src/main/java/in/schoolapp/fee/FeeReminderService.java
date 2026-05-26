package in.schoolapp.fee;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.communication.template.MessageTemplateService;
import in.schoolapp.fee.dto.BulkReminderRequest;
import in.schoolapp.payment.PaymentLinkService;
import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Fee reminder dispatch — manual endpoint for now; Slice 8 adds a scheduled runner that
 * consumes {@code fee_reminder_schedules} config per tenant (gap analysis §5.3).
 * <p>
 * Each reminder includes a payment link generated via {@link PaymentLinkService} — the real
 * gateway is selected by {@code app.payment.provider}. If link creation fails (e.g. provider
 * outage), we still send the reminder without a link rather than skip the entire batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeReminderService {

    private final FeeInvoiceService invoiceService;
    private final StudentService studentService;
    private final FamilyService familyService;
    private final SchoolService schoolService;
    private final MessageTemplateService templates;
    private final WhatsAppNotifier whatsAppNotifier;
    private final PaymentLinkService paymentLinkService;

    @Async("notificationExecutor")
    public void dispatchReminders(UUID tenantId, BulkReminderRequest req) {
        School school = schoolService.getSchoolEntity(tenantId);

        for (UUID studentId : req.studentIds()) {
            try {
                Student student = studentService.getStudentEntity(tenantId, studentId);
                long outstanding = invoiceService.getOutstanding(studentId);
                if (outstanding <= 0) {
                    log.debug("Skipping reminder for student={} — no outstanding balance", studentId);
                    continue;
                }

                Optional<Parent> primary = familyService.getPrimaryParent(studentId);
                if (primary.isEmpty() || primary.get().getPhone() == null) {
                    log.warn("Skipping reminder for student={} — no primary parent phone", studentId);
                    continue;
                }
                Parent parent = primary.get();

                String paymentUrl = buildPaymentUrlOrNull(tenantId, student, parent, outstanding);
                String body = req.messageOverride() != null && !req.messageOverride().isBlank()
                    ? req.messageOverride()
                    : templates.feeReminder(
                        school.getName(), parent.getName(), student, outstanding, paymentUrl);

                WhatsAppMessage.Audit audit = WhatsAppMessage.Audit.forParent(
                    tenantId, studentId, parent.getId(), parent.getName());
                whatsAppNotifier.send(WhatsAppMessage.text(
                    parent.getPhone(), body, MessageType.FEE_REMINDER, audit));
            } catch (AppException e) {
                log.warn("Reminder skipped for student={} — {}", studentId, e.getMessage());
            }
        }
    }

    public int queueBulkReminders(UUID tenantId, BulkReminderRequest req) {
        if (req.studentIds().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "No student IDs provided");
        }
        dispatchReminders(tenantId, req);
        return req.studentIds().size();
    }

    private String buildPaymentUrlOrNull(UUID tenantId, Student student, Parent parent, long amountPaise) {
        try {
            String purpose = "Fee for " + student.displayName();
            PaymentLink link = paymentLinkService.createLink(
                tenantId, student.getId(), amountPaise, purpose,
                new PaymentLinkRequest.PayerInfo(parent.getName(), parent.getPhone(), parent.getEmail())
            );
            return link.url();
        } catch (Exception e) {
            // Degrade gracefully — a reminder without a link is still useful.
            log.warn("Payment link creation failed student={} — sending reminder without link: {}",
                student.getId(), e.getMessage());
            return null;
        }
    }
}
