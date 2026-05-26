package in.schoolapp.fee;

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

    @Transactional
    public DiscountResponse create(UUID tenantId, CreateDiscountRequest req) {
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
        d.setActive(true);
        d = discountRepository.save(d);
        log.info("Discount created tenant={} student={} type={}", tenantId, req.studentId(), req.discountType());
        return DiscountResponse.from(d);
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
