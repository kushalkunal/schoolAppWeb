package in.schoolapp.fee;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.fee.dto.PaymentResponse;
import in.schoolapp.fee.dto.QuickCollectRequest;
import in.schoolapp.fee.dto.StudentFeeSummaryResponse;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.FeePayment;
import in.schoolapp.fee.entity.PaymentMode;
import in.schoolapp.fee.event.FeePaymentCreatedEvent;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.entity.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Zero-config quick-collect per gap analysis §5.3. Payment rows are the source of financial
 * truth. If an invoice id is supplied, the payment is applied to that invoice with partial-
 * payment accumulation. Otherwise the payment is applied FIFO to the student's pending
 * invoices; any excess is recorded as a standalone payment (no implicit invoice creation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeePaymentService {

    private final FeePaymentRepository paymentRepository;
    private final FeeInvoiceService invoiceService;
    private final ReceiptService receiptService;
    private final StudentService studentService;
    private final SchoolService schoolService;
    private final ApplicationEventPublisher events;
    private final AuditLogger auditLogger;

    @Transactional
    public PaymentResponse quickCollect(UUID tenantId, QuickCollectRequest req) {
        Student student = studentService.getStudentEntity(tenantId, req.studentId());
        School school = schoolService.getSchoolEntity(tenantId);

        if (req.amountPaise() == null || req.amountPaise() <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Amount must be positive");
        }
        long amount = req.amountPaise();

        // Apply to invoices — explicit if provided, otherwise FIFO over pending
        UUID appliedInvoiceId = null;
        if (req.invoiceId() != null) {
            FeeInvoice inv = invoiceService.getInvoiceOrThrow(tenantId, req.invoiceId());
            if (!inv.getStudentId().equals(student.getId())) {
                throw new AppException(ErrorCode.INVOICE_NOT_FOUND,
                    "Invoice does not belong to this student");
            }
            long due = inv.balancePaise();
            if (amount > due) {
                throw new AppException(ErrorCode.PAYMENT_AMOUNT_EXCEEDS_DUE,
                    "Payment exceeds invoice balance: ₹" + ReceiptService.formatAmount(due));
            }
            inv.applyPayment(amount);
            invoiceService.save(inv);
            appliedInvoiceId = inv.getId();
        } else {
            // FIFO application; excess becomes standalone (no auto-invoice in Slice 4)
            long remaining = amount;
            List<FeeInvoice> pending = invoiceService.findPendingForStudent(student.getId());
            for (FeeInvoice inv : pending) {
                if (remaining <= 0) break;
                long payable = Math.min(remaining, inv.balancePaise());
                inv.applyPayment(payable);
                invoiceService.save(inv);
                remaining -= payable;
                if (appliedInvoiceId == null) appliedInvoiceId = inv.getId();
            }
            // remaining > 0: standalone payment; no error — system accepts overpayment/advance
        }

        // Generate receipt number + persist payment
        long seq = receiptService.nextSequence(tenantId);
        String receiptNumber = ReceiptService.formatReceiptNumber(seq, Year.now().getValue());

        FeePayment payment = new FeePayment();
        payment.setSchoolId(tenantId);
        payment.setStudentId(student.getId());
        payment.setInvoiceId(appliedInvoiceId);
        payment.setFeeHeadId(req.feeHeadId());
        payment.setAmountPaise(amount);
        payment.setPaymentMode(req.paymentMode());
        payment.setReceiptNumber(receiptNumber);
        payment.setPaymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now());
        payment.setCollectedById(TenantContext.getStaffId());
        payment.setNotes(req.notes());
        payment = paymentRepository.save(payment);

        // PDF receipt — stored locally for Slice 4, R2-uploaded in Slice 5
        byte[] pdfBytes = receiptService.generatePdf(payment, student, school);
        String pdfUrl = receiptService.storeReceipt(tenantId, payment.getId(), pdfBytes);
        payment.setReceiptPdfUrl(pdfUrl);
        payment = paymentRepository.save(payment);

        log.info("Collected payment id={} tenantId={} studentId={} amount={} receipt={}",
            payment.getId(), tenantId, student.getId(), amount, receiptNumber);

        auditLogger.logCreate(tenantId, "FeePayment", payment.getId(), java.util.Map.of(
            "studentId", student.getId(),
            "invoiceId", appliedInvoiceId == null ? "null" : appliedInvoiceId.toString(),
            "amountPaise", amount,
            "paymentMode", req.paymentMode() == null ? "null" : req.paymentMode().name(),
            "receiptNumber", receiptNumber
        ));

        long outstanding = invoiceService.getOutstanding(student.getId());

        // Fire-and-forget: WhatsApp receipt dispatch via ReceiptDeliveryListener
        events.publishEvent(new FeePaymentCreatedEvent(
            tenantId, payment.getId(), student.getId(), amount, receiptNumber, pdfUrl));

        return PaymentResponse.from(payment, outstanding);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID tenantId, UUID paymentId) {
        FeePayment p = paymentRepository.findByIdAndSchoolId(paymentId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RECEIPT_NOT_FOUND, "Payment", paymentId));
        return PaymentResponse.from(p, invoiceService.getOutstanding(p.getStudentId()));
    }

    /**
     * Records a payment migrated from a paper receipt — see
     * {@link in.schoolapp.migration.MigrationJobService}. Mark {@code historical=true} so
     * downstream listeners (receipt PDF + WhatsApp delivery) do NOT fire — the parent
     * already has the original paper receipt; we're only digitising the audit trail.
     */
    @Transactional
    public FeePayment createHistorical(
        UUID tenantId, UUID studentId, long amountPaise,
        in.schoolapp.fee.entity.PaymentMode paymentMode,
        LocalDate paymentDate, String externalReceiptNumber, String notes
    ) {
        long seq = receiptService.nextSequence(tenantId);
        // Prefix imported receipts with "HIST-" so they're visually distinct from new ones.
        String receiptNumber = "HIST-" + ReceiptService.formatReceiptNumber(seq, Year.now().getValue());

        FeePayment payment = new FeePayment();
        payment.setSchoolId(tenantId);
        payment.setStudentId(studentId);
        payment.setAmountPaise(amountPaise);
        payment.setPaymentMode(paymentMode != null ? paymentMode : in.schoolapp.fee.entity.PaymentMode.CASH);
        payment.setReceiptNumber(receiptNumber);
        payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        payment.setHistorical(true);
        payment.setNotes(buildHistoricalNote(externalReceiptNumber, notes));
        payment = paymentRepository.save(payment);

        log.info("Recorded historical payment id={} tenantId={} studentId={} amount={} sourceReceipt={}",
            payment.getId(), tenantId, studentId, amountPaise, externalReceiptNumber);
        // No event published — historical payments don't dispatch WhatsApp messages.
        return payment;
    }

    private static String buildHistoricalNote(String externalReceiptNumber, String userNote) {
        StringBuilder sb = new StringBuilder("Migrated from paper receipt");
        if (externalReceiptNumber != null && !externalReceiptNumber.isBlank()) {
            sb.append(" #").append(externalReceiptNumber.trim());
        }
        if (userNote != null && !userNote.isBlank()) {
            sb.append(" — ").append(userNote.trim());
        }
        return sb.toString();
    }

    /**
     * Records a payment that was completed via the online gateway (Stripe, Razorpay, …). Called
     * by {@link in.schoolapp.payment.PaymentEventListener} on a verified {@code PAID} webhook.
     * Idempotent by {@code providerReference}: a repeated webhook for the same session returns
     * the existing payment rather than creating a duplicate.
     * <p>
     * Fires {@link FeePaymentCreatedEvent} on creation so the receipt flows out to the parent —
     * closing the loop of "parent taps link → pays → receipt WhatsApp-ed" without any admin
     * action.
     */
    @Transactional
    public FeePayment createOnlinePayment(
        UUID tenantId, UUID studentId, long amountPaise, String providerReference,
        String providerPaymentMethod
    ) {
        if (providerReference == null || providerReference.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "providerReference required for online payment");
        }
        Optional<FeePayment> existing = paymentRepository.findByProviderReference(providerReference);
        if (existing.isPresent()) {
            log.info("Online payment already recorded ref={} — idempotent skip", providerReference);
            return existing.get();
        }

        Student student = studentService.getStudentEntity(tenantId, studentId);
        School school = schoolService.getSchoolEntity(tenantId);

        // Apply FIFO to pending invoices — mirrors quickCollect. Excess becomes standalone.
        UUID appliedInvoiceId = null;
        long remaining = amountPaise;
        List<FeeInvoice> pending = invoiceService.findPendingForStudent(student.getId());
        for (FeeInvoice inv : pending) {
            if (remaining <= 0) break;
            long payable = Math.min(remaining, inv.balancePaise());
            inv.applyPayment(payable);
            invoiceService.save(inv);
            remaining -= payable;
            if (appliedInvoiceId == null) appliedInvoiceId = inv.getId();
        }

        long seq = receiptService.nextSequence(tenantId);
        String receiptNumber = ReceiptService.formatReceiptNumber(seq, Year.now().getValue());

        FeePayment payment = new FeePayment();
        payment.setSchoolId(tenantId);
        payment.setStudentId(student.getId());
        payment.setInvoiceId(appliedInvoiceId);
        payment.setAmountPaise(amountPaise);
        payment.setPaymentMode(PaymentMode.ONLINE);
        payment.setReceiptNumber(receiptNumber);
        payment.setPaymentDate(LocalDate.now());
        payment.setProviderReference(providerReference);
        payment.setNotes(providerPaymentMethod == null ? "Online payment"
            : "Online payment via " + providerPaymentMethod);
        payment = paymentRepository.save(payment);

        byte[] pdfBytes = receiptService.generatePdf(payment, student, school);
        String pdfUrl = receiptService.storeReceipt(tenantId, payment.getId(), pdfBytes);
        payment.setReceiptPdfUrl(pdfUrl);
        payment = paymentRepository.save(payment);

        log.info("Recorded online payment id={} tenantId={} studentId={} amount={} ref={}",
            payment.getId(), tenantId, student.getId(), amountPaise, providerReference);

        auditLogger.logCreate(tenantId, "FeePayment", payment.getId(), java.util.Map.of(
            "studentId", student.getId(),
            "amountPaise", amountPaise,
            "paymentMode", "ONLINE",
            "providerReference", providerReference,
            "receiptNumber", receiptNumber
        ));

        events.publishEvent(new FeePaymentCreatedEvent(
            tenantId, payment.getId(), student.getId(), amountPaise, receiptNumber, pdfUrl));
        return payment;
    }

    @Transactional(readOnly = true)
    public StudentFeeSummaryResponse getStudentSummary(UUID tenantId, UUID studentId) {
        studentService.getStudentEntity(tenantId, studentId);  // tenant boundary check
        long outstanding = invoiceService.getOutstanding(studentId);
        var invoices = invoiceService.listInvoicesForStudent(studentId);
        var payments = paymentRepository.findByStudentIdOrderByPaymentDateDesc(studentId).stream()
            .limit(20)
            .map(p -> PaymentResponse.from(p, outstanding))
            .toList();
        long totalPaid = payments.stream().mapToLong(PaymentResponse::amountPaise).sum();
        return new StudentFeeSummaryResponse(studentId, outstanding, totalPaid, invoices, payments);
    }
}
