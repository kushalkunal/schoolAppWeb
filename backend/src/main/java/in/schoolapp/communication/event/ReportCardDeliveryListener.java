package in.schoolapp.communication.event;

import in.schoolapp.academics.event.ReportCardGeneratedEvent;
import in.schoolapp.academics.repository.ReportCardRepository;
import in.schoolapp.communication.dispatcher.EmailSender;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.feature.FeatureFlagService;
import in.schoolapp.feature.FeatureKey;
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

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Delivers generated report cards to the primary parent via WhatsApp. Uses
 * {@code AFTER_COMMIT} so a rolled-back generation transaction never leaks messages. Marks
 * {@code report_cards.wa_sent_at} on success — webhook updates will later fill in
 * {@code wa_delivered_at} and {@code wa_read_at}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCardDeliveryListener {

    private final StudentService studentService;
    private final SchoolService schoolService;
    private final FamilyService familyService;
    private final ReportCardRepository reportCardRepository;
    private final WhatsAppNotifier whatsAppNotifier;
    private final EmailSender emailSender;
    private final FeatureFlagService featureFlagService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCardGenerated(ReportCardGeneratedEvent event) {
        try {
            if (!featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFICATIONS)
                || !featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFY_REPORT_CARD)) {
                return;
            }
            Student student = studentService.getStudentEntity(event.tenantId(), event.studentId());
            School school = schoolService.getSchoolEntity(event.tenantId());

            Optional<Parent> parentOpt = familyService.getPrimaryParent(event.studentId());
            if (parentOpt.isEmpty() || parentOpt.get().getPhone() == null) {
                log.warn("Report card not sent — no primary parent phone for student={}", event.studentId());
                return;
            }
            Parent parent = parentOpt.get();

            String body = String.format(
                "📄 *%s's Report Card is ready!*\n\n%s — %s.\nView your child's marks, percentage and class rank in the attached PDF.\n\n— %s",
                student.displayName(),
                student.displayName(),
                event.examName(),
                school.getName()
            );

            WhatsAppMessage.Audit audit = WhatsAppMessage.Audit.forParent(
                event.tenantId(), event.studentId(), parent.getId(), parent.getName());
            whatsAppNotifier.send(WhatsAppMessage.withMedia(
                parent.getPhone(), body, event.pdfUrl(), MessageType.REPORT_CARD, audit));
            if (parent.getEmail() != null && !parent.getEmail().isBlank()) {
                try {
                    emailSender.send(parent.getEmail(),
                        school.getName() + " — " + event.examName() + " report card",
                        body + "\n\nPDF: " + event.pdfUrl());
                } catch (Exception e) {
                    log.warn("Report card email failed parent={} — {}", parent.getId(), e.getMessage());
                }
            }

            // Record dispatch timestamp — delivery/read timestamps come via webhook in Slice 6
            reportCardRepository.findById(event.reportCardId()).ifPresent(card -> {
                card.setWaSentAt(OffsetDateTime.now());
                reportCardRepository.save(card);
            });
        } catch (Exception e) {
            log.error("Report card delivery failed reportCardId={} — {}",
                event.reportCardId(), e.getMessage());
        }
    }
}
