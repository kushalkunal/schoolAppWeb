package in.schoolapp.fee;

import in.schoolapp.approval.ApprovalService;
import in.schoolapp.approval.dto.ApprovalRequestResponse;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.dto.CreateDiscountRequest;
import in.schoolapp.fee.dto.DiscountResponse;
import in.schoolapp.fee.entity.FeeDiscount;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.repository.FeeDiscountRepository;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Discount lookup + write surface. Two callers:
 * <ul>
 *   <li>{@link FeeInvoiceService} consults {@link #computeApplicableDiscount} during invoice
 *       creation to snapshot {@code discount_applied_paise} into the invoice.</li>
 *   <li>{@link in.schoolapp.fee.FeeController} exposes CRUD endpoints for schools to define
 *       their discount catalogue per student.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeDiscountService {

    private final FeeDiscountRepository discountRepository;
    private final FeeInvoiceRepository invoiceRepository;
    private final ApprovalService approvalService;

    /**
     * Stages a discount for maker-checker approval (audit fix #6). The {@link FeeDiscount} is
     * persisted {@code active=false} so it is invisible to {@code findApplicable} until a different
     * user approves the returned request; the {@link FeeDiscountApprovalHandler} then flips it live.
     */
    @Transactional
    public ApprovalRequestResponse create(UUID tenantId, CreateDiscountRequest req) {
        if ((req.percent() == null) == (req.fixedPaise() == null)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Exactly one of percent or fixedPaise must be set");
        }
        FeeDiscount d = new FeeDiscount();
        d.setSchoolId(tenantId);
        d.setStudentId(req.studentId());
        d.setFeeHeadId(req.feeHeadId());
        d.setDiscountType(req.discountType());
        d.setPercent(req.percent());
        d.setFixedPaise(req.fixedPaise());
        d.setValidFrom(req.validFrom() != null ? req.validFrom() : LocalDate.now());
        d.setValidUntil(req.validUntil());
        d.setReason(req.reason());
        d.setActive(false);   // inert until approved
        d = discountRepository.save(d);

        long amount = req.fixedPaise() != null ? req.fixedPaise() : 0L;
        String summary = "Discount " + req.discountType() + " for student " + req.studentId()
            + (req.percent() != null ? " (" + req.percent() + "%)" : " (₹" + (amount / 100) + ")");
        var approval = approvalService.submit(
            tenantId, ApprovalType.FEE_DISCOUNT, d.getId(), null, amount, summary);
        log.info("Discount staged for approval tenant={} student={} type={} approvalId={}",
            tenantId, req.studentId(), req.discountType(), approval.getId());
        return ApprovalRequestResponse.from(approval);
    }

    public List<DiscountResponse> list(UUID tenantId, UUID studentId) {
        return discountRepository.findBySchoolIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId)
            .stream().map(DiscountResponse::from).toList();
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID discountId) {
        FeeDiscount d = discountRepository.findById(discountId)
            .filter(x -> x.getSchoolId().equals(tenantId))
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Discount not found"));
        d.setActive(false);
        discountRepository.save(d);
    }

    /**
     * Snapshot the discount that should apply if an invoice for {@code (studentId, feeHeadId)}
     * with gross {@code grossPaise} were generated today. Returns 0 when no discount applies.
     *
     * <p>If multiple discounts overlap (e.g. blanket sibling + head-specific scholarship), the
     * one with the largest absolute reduction wins. This is the "best-for-student" policy;
     * schools that need stacking semantics can extend this method.
     */
    public long computeApplicableDiscount(UUID schoolId, UUID studentId, UUID feeHeadId, long grossPaise) {
        var candidates = discountRepository.findApplicable(schoolId, studentId, feeHeadId, LocalDate.now());
        long best = 0;
        for (FeeDiscount d : candidates) {
            long reduction = d.getFixedPaise() != null
                ? d.getFixedPaise()
                : BigDecimal.valueOf(grossPaise)
                    .multiply(d.getPercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
            if (reduction > best) best = reduction;
        }
        return Math.min(best, grossPaise);    // never discount below zero
    }

    /**
     * On-demand snapshot for a specific invoice (used by web preview "what's the net?").
     * Doesn't write to the invoice.
     */
    public long previewForInvoice(UUID schoolId, UUID invoiceId) {
        FeeInvoice inv = invoiceRepository.findByIdAndSchoolId(invoiceId, schoolId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Invoice not found"));
        return computeApplicableDiscount(schoolId, inv.getStudentId(), inv.getFeeHeadId(),
            inv.getAmountDuePaise() + inv.getDiscountAppliedPaise());
    }
}
