package in.schoolapp.fee;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.dto.CreateInvoiceRequest;
import in.schoolapp.fee.dto.InvoiceResponse;
import in.schoolapp.fee.dto.OpeningBalanceRequest;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Invoice lifecycle. Quick-collect payments don't need invoices; invoices exist for structured
 * billing (term fees, monthly fees, opening balances carried forward from paper ledgers).
 * Payments can be applied to invoices FIFO by {@link FeePaymentService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeInvoiceService {

    private final FeeInvoiceRepository invoiceRepository;

    @Transactional
    public InvoiceResponse createInvoice(UUID tenantId, CreateInvoiceRequest req) {
        FeeInvoice inv = new FeeInvoice();
        inv.setSchoolId(tenantId);
        inv.setStudentId(req.studentId());
        inv.setFeeHeadId(req.feeHeadId());
        inv.setAmountDuePaise(req.amountDuePaise());
        inv.setDueDate(req.dueDate());
        inv.setDescription(req.description());
        inv.setStatus(InvoiceStatus.PENDING);
        inv = invoiceRepository.save(inv);
        log.info("Created invoice id={} tenantId={} studentId={} amount={}",
            inv.getId(), tenantId, inv.getStudentId(), inv.getAmountDuePaise());
        return InvoiceResponse.from(inv);
    }

    /**
     * Bulk opening-balance import for paper-register migration (gap analysis §7).
     * One invoice per student, tagged {@code openingBalance=true}.
     */
    @Transactional
    public List<InvoiceResponse> recordOpeningBalances(UUID tenantId, OpeningBalanceRequest req) {
        return req.balances().stream().map(entry -> {
            FeeInvoice inv = new FeeInvoice();
            inv.setSchoolId(tenantId);
            inv.setStudentId(entry.studentId());
            inv.setAmountDuePaise(entry.amountPaise());
            inv.setOpeningBalance(true);
            inv.setStatus(entry.amountPaise() > 0 ? InvoiceStatus.PENDING : InvoiceStatus.PAID);
            inv.setDescription(entry.note() == null || entry.note().isBlank()
                ? "Opening balance (carried forward)" : entry.note());
            return InvoiceResponse.from(invoiceRepository.save(inv));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoicesForStudent(UUID studentId) {
        return invoiceRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .map(InvoiceResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getOutstanding(UUID studentId) {
        return invoiceRepository.sumOutstandingByStudent(studentId);
    }

    FeeInvoice getInvoiceOrThrow(UUID tenantId, UUID invoiceId) {
        return invoiceRepository.findByIdAndSchoolId(invoiceId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.INVOICE_NOT_FOUND, "Invoice", invoiceId));
    }

    List<FeeInvoice> findPendingForStudent(UUID studentId) {
        return invoiceRepository.findByStudentIdAndStatusInOrderByDueDateAscCreatedAtAsc(
            studentId, List.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIAL));
    }

    void save(FeeInvoice invoice) {
        invoiceRepository.save(invoice);
    }
}
