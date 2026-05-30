package in.schoolapp.communication.template;

import in.schoolapp.communication.entity.MessageTemplate;
import in.schoolapp.communication.repository.MessageTemplateRepository;
import in.schoolapp.fee.ReceiptService;
import in.schoolapp.student.entity.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Centralises message body composition. Templates are first looked up in the
 * {@code message_templates} table (per-tenant overrides); if none is stored the built-in
 * default is used. This allows schools to customise their notifications without a code change.
 * <p>
 * Template body strings may contain {@code {placeholder}} tokens that are substituted at
 * render time.
 */
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final MessageTemplateRepository templateRepo;

    public static final String KEY_ABSENCE_ALERT = "ABSENCE_ALERT";
    public static final String KEY_FEE_RECEIPT   = "FEE_RECEIPT";
    public static final String KEY_FEE_REMINDER  = "PARENT_NOTIFY_FEE_DUE";
    public static final String KEY_FEE_OVERDUE   = "PARENT_NOTIFY_FEE_OVERDUE";

    // ── Absence ──────────────────────────────────────────────────────────────

    public String absenceAlert(UUID schoolId, String schoolName, String parentName,
                               Student child, LocalDate date) {
        Optional<MessageTemplate> tpl = templateRepo.findBySchoolIdAndTemplateKey(schoolId, KEY_ABSENCE_ALERT);
        if (tpl.isPresent()) {
            return tpl.get().getBodyTemplate()
                .replace("{parentName}", parentName != null ? parentName : "Parent")
                .replace("{studentName}", child.displayName())
                .replace("{date}", date.format(DATE_FMT))
                .replace("{schoolName}", schoolName);
        }
        return absenceAlert(schoolName, parentName, child, date);
    }

    /** Legacy overload — uses built-in default (no DB lookup). */
    public String absenceAlert(String schoolName, String parentName, Student child, LocalDate date) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        return String.format(
            "%s,\n\n%s was marked ABSENT today (%s). If this is incorrect, please contact the school.\n\n— %s",
            greeting, child.displayName(), date.format(DATE_FMT), schoolName);
    }

    public String siblingAbsenceAlert(String schoolName, String parentName,
                                      List<Student> absentChildren, LocalDate date) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        String names = absentChildren.stream()
            .map(Student::displayName)
            .reduce((a, b) -> a + " and " + b)
            .orElse("");
        return String.format(
            "%s,\n\n%s were both marked ABSENT today (%s). If this is incorrect, please contact the school.\n\n— %s",
            greeting, names, date.format(DATE_FMT), schoolName);
    }

    public String lateArrivalAlert(String schoolName, String parentName, Student child, LocalDate date) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        return String.format(
            "%s,\n\n%s arrived LATE to school today (%s).\n\n— %s",
            greeting, child.displayName(), date.format(DATE_FMT), schoolName);
    }

    // ── Fee receipt ──────────────────────────────────────────────────────────

    public String feeReceipt(UUID schoolId, String schoolName, Student student,
                             long amountPaise, String receiptNumber, String paymentMode,
                             LocalDate paymentDate, long outstandingPaise) {
        Optional<MessageTemplate> tpl = templateRepo.findBySchoolIdAndTemplateKey(schoolId, KEY_FEE_RECEIPT);
        if (tpl.isPresent()) {
            return tpl.get().getBodyTemplate()
                .replace("{schoolName}", schoolName)
                .replace("{studentName}", student.displayName())
                .replace("{receiptNumber}", receiptNumber)
                .replace("{date}", paymentDate.format(DATE_FMT))
                .replace("{paymentMode}", paymentMode)
                .replace("{amountPaid}", ReceiptService.formatAmount(amountPaise))
                .replace("{balance}", ReceiptService.formatAmount(outstandingPaise));
        }
        return feeReceipt(schoolName, student, amountPaise, receiptNumber, paymentMode, paymentDate, outstandingPaise);
    }

    /** Legacy overload — uses built-in default. */
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
            schoolName, student.displayName(), receiptNumber,
            paymentDate.format(DATE_FMT), paymentMode,
            ReceiptService.formatAmount(amountPaise),
            ReceiptService.formatAmount(outstandingPaise));
    }

    // ── Fee reminder ─────────────────────────────────────────────────────────

    public String feeReminder(UUID schoolId, String schoolName, String parentName,
                              Student student, long outstandingPaise, String paymentLink) {
        Optional<MessageTemplate> tpl = templateRepo.findBySchoolIdAndTemplateKey(schoolId, KEY_FEE_REMINDER);
        if (tpl.isPresent()) {
            String payLine = paymentLink == null || paymentLink.isBlank() ? "" : "\nPay now: " + paymentLink;
            return tpl.get().getBodyTemplate()
                .replace("{parentName}", parentName != null ? parentName : "Parent")
                .replace("{studentName}", student.displayName())
                .replace("{outstanding}", ReceiptService.formatAmount(outstandingPaise))
                .replace("{paymentLink}", payLine)
                .replace("{schoolName}", schoolName);
        }
        return feeReminder(schoolName, parentName, student, outstandingPaise, paymentLink);
    }

    /** Legacy overload — uses built-in default. */
    public String feeReminder(String schoolName, String parentName, Student student,
                              long outstandingPaise, String paymentLink) {
        String greeting = parentName == null || parentName.isBlank() ? "Dear Parent" : "Dear " + parentName;
        String payLine = paymentLink == null || paymentLink.isBlank()
            ? "" : "\nPay now: " + paymentLink;
        return String.format(
            "%s,\n\n₹%s in fees is outstanding for %s. Please pay at your earliest convenience.%s\n\n— %s",
            greeting, ReceiptService.formatAmount(outstandingPaise), student.displayName(), payLine, schoolName);
    }

    // ── Template management helpers ──────────────────────────────────────────

    /** All well-known template keys surfaced in the admin UI. */
    public List<String> knownKeys() {
        return List.of(KEY_ABSENCE_ALERT, KEY_FEE_RECEIPT, KEY_FEE_REMINDER, KEY_FEE_OVERDUE);
    }

    /**
     * Returns the compiled default body for a given key so the UI can show what will be sent
     * when no override is stored. Placeholders are shown as-is (not interpolated).
     */
    public String defaultBody(String key) {
        return switch (key) {
            case KEY_ABSENCE_ALERT -> """
                Dear {parentName},

                {studentName} was marked ABSENT today ({date}). If this is incorrect, please contact the school.

                — {schoolName}""";
            case KEY_FEE_RECEIPT -> """
                🧾 *Fee Receipt — {schoolName}*
                ━━━━━━━━━━━━━━━━━━
                Student: {studentName}
                Receipt #: {receiptNumber}
                Date: {date}
                Mode: {paymentMode}
                ━━━━━━━━━━━━━━━━━━
                Amount Paid: ₹{amountPaid}
                Balance Due: ₹{balance}
                ━━━━━━━━━━━━━━━━━━
                Thank you!""";
            case KEY_FEE_REMINDER -> """
                Dear {parentName},

                ₹{outstanding} in fees is outstanding for {studentName}. Please pay at your earliest convenience.{paymentLink}

                — {schoolName}""";
            case KEY_FEE_OVERDUE -> """
                Dear {parentName},

                {studentName} has overdue fees totalling ₹{outstanding}. Please clear the dues to avoid penalties.{paymentLink}

                — {schoolName}""";
            default -> "(no default — custom key)";
        };
    }
}
