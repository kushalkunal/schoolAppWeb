package in.schoolapp.cashrecon;

import in.schoolapp.cashrecon.dto.CashReconciliationResponse;
import in.schoolapp.cashrecon.dto.CloseDrawerRequest;
import in.schoolapp.cashrecon.dto.DayTotalsResponse;
import in.schoolapp.approval.ApprovalService;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.cashrecon.entity.CashReconciliation;
import in.schoolapp.common.TenantContext;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.fee.repository.FeePaymentRepository.ModeTotalRow;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Slice 34 — day-end cash reconciliation.
 *
 * <p>"Expected" totals come from {@code fee_payments} aggregated by payment_mode for the day.
 * The accountant supplies the physical counts; we persist both and store the variance so it
 * can be audited later.
 */
@Service
@RequiredArgsConstructor
public class CashReconciliationService {

    private final CashReconciliationRepository repo;
    private final FeePaymentRepository paymentRepo;
    private final ApprovalService approvalService;

    /** Absolute variance (paise) above which a drawer close must be reviewed. Default 0 = any
     *  non-zero over/short escalates; raise per tenant policy to cut noise. */
    @Value("${app.cashrecon.variance-threshold-paise:0}")
    private long varianceThresholdPaise;

    @Transactional(readOnly = true)
    public DayTotalsResponse expectedForDay(UUID tenantId, LocalDate date) {
        List<ModeTotalRow> rows = paymentRepo.sumByModeBetween(tenantId, date, date);
        long cash = 0, upi = 0, cheque = 0, other = 0;
        int count = 0;
        for (ModeTotalRow r : rows) {
            count += r.getPaymentCount().intValue();
            switch (r.getMode()) {
                case "CASH"          -> cash   += r.getAmountPaise();
                case "ONLINE"        -> upi    += r.getAmountPaise();
                case "CHEQUE", "DD"  -> cheque += r.getAmountPaise();
                default              -> other  += r.getAmountPaise();
            }
        }
        return new DayTotalsResponse(date, cash, upi, cheque, other, cash + upi + cheque + other, count);
    }

    @Transactional
    public CashReconciliationResponse closeDrawer(UUID tenantId, CloseDrawerRequest req) {
        DayTotalsResponse expected = expectedForDay(tenantId, req.date());
        long counted = req.countedCashPaise() + req.countedUpiPaise()
            + req.countedChequePaise() + req.countedOtherPaise();
        long variance = counted - expected.totalPaise();

        CashReconciliation r = new CashReconciliation();
        r.setSchoolId(tenantId);
        r.setClosedById(TenantContext.getStaffId());
        r.setClosedOnDate(req.date());
        r.setExpectedCashPaise(expected.expectedCashPaise());
        r.setExpectedUpiPaise(expected.expectedUpiPaise());
        r.setExpectedChequePaise(expected.expectedChequePaise());
        r.setExpectedOtherPaise(expected.expectedOtherPaise());
        r.setCountedCashPaise(req.countedCashPaise());
        r.setCountedUpiPaise(req.countedUpiPaise());
        r.setCountedChequePaise(req.countedChequePaise());
        r.setCountedOtherPaise(req.countedOtherPaise());
        r.setVariancePaise(variance);
        r.setNotes(req.notes());

        // Escalate an over/short above the threshold for checker sign-off (audit #9). The record is
        // still saved (the count is a fact); it stays unreviewed until the approval is granted.
        boolean escalate = Math.abs(variance) > varianceThresholdPaise;
        r.setVarianceReviewed(!escalate);
        r = repo.save(r);

        if (escalate) {
            String sign = variance < 0 ? "short" : "over";
            approvalService.submit(tenantId, ApprovalType.CASH_VARIANCE, r.getId(), null,
                Math.abs(variance),
                "Cash drawer " + sign + " by ₹" + (Math.abs(variance) / 100) + " on " + req.date());
        }
        return CashReconciliationResponse.from(r);
    }

    @Transactional(readOnly = true)
    public List<CashReconciliationResponse> history(UUID tenantId) {
        return repo.findBySchoolIdOrderByClosedOnDateDesc(tenantId).stream()
            .map(CashReconciliationResponse::from).toList();
    }
}
