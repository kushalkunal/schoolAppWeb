package in.schoolapp.communication.template;

import in.schoolapp.fee.ReceiptService;
import in.schoolapp.student.entity.Student;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Centralises message body composition. Separating template rendering from dispatch means
 * Slice 5's Meta-approved template numbers can be swapped in without touching
 * AbsenceAlertService/ReceiptDeliveryListener.
 * <p>
 * Templates are intentionally plain strings for Slice 4 — real WhatsApp BSPs require
 * pre-approved template IDs + variable arrays. Once templates are approved, we'll add a
 * {@code WhatsAppTemplateId} enum and dispatch those instead of free-form text.
 */
@Service
public class MessageTemplateService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public String absenceAlert(String schoolName, String parentName, Student child, LocalDate date) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        return String.format(
            "%s,\n\n%s was marked ABSENT today (%s). If this is incorrect, please contact the school.\n\n— %s",
            greeting, child.displayName(), date.format(DATE_FMT), schoolName
        );
    }

    /** Sibling-aware combined message — one per parent, not per child. */
    public String siblingAbsenceAlert(String schoolName, String parentName,
                                      List<Student> absentChildren, LocalDate date) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        String names = absentChildren.stream()
            .map(Student::displayName)
            .reduce((a, b) -> a + " and " + b)
            .orElse("");
        return String.format(
            "%s,\n\n%s were both marked ABSENT today (%s). If this is incorrect, please contact the school.\n\n— %s",
            greeting, names, date.format(DATE_FMT), schoolName
        );
    }

    public String lateArrivalAlert(String schoolName, String parentName, Student child, LocalDate date) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        return String.format(
            "%s,\n\n%s arrived LATE to school today (%s).\n\n— %s",
            greeting, child.displayName(), date.format(DATE_FMT), schoolName
        );
    }

    public String feeReceipt(String schoolName, Student student, long amountPaise,
                             String receiptNumber, String paymentMode, LocalDate paymentDate,
                             long outstandingPaise) {
        return String.format("""
            🧾 *Fee Receipt — %s*
            ━━━━━━━━━━━━━━━━━━
            Student: %s
            Receipt #: %s
            Date: %s
            Mode: %s
            ━━━━━━━━━━━━━━━━━━
            Amount Paid: ₹%s
            Balance Due: ₹%s
            ━━━━━━━━━━━━━━━━━━
            Thank you!""",
            schoolName,
            student.displayName(),
            receiptNumber,
            paymentDate.format(DATE_FMT),
            paymentMode,
            ReceiptService.formatAmount(amountPaise),
            ReceiptService.formatAmount(outstandingPaise)
        );
    }

    public String feeReminder(String schoolName, String parentName, Student student,
                              long outstandingPaise, String paymentLink) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        String payLine = paymentLink == null || paymentLink.isBlank()
            ? ""
            : "\nPay now: " + paymentLink;
        return String.format(
            "%s,\n\n₹%s in fees is outstanding for %s. Please pay at your earliest convenience.%s\n\n— %s",
            greeting,
            ReceiptService.formatAmount(outstandingPaise),
            student.displayName(),
            payLine,
            schoolName
        );
    }
}
