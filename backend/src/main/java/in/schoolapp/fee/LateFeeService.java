package in.schoolapp.fee;

import in.schoolapp.fee.entity.FeeAdjustment;
import in.schoolapp.fee.entity.FeeHead;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.repository.FeeAdjustmentRepository;
import in.schoolapp.fee.repository.FeeHeadRepository;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Applies cumulative late fees to overdue invoices. The math is:
 *
 * <pre>
 *   days_overdue   = today − (due_date + grace_days)
 *   raw_fee_paise  = days_overdue × fee_head.late_fee_paise_per_day
 *   capped_fee     = min(raw_fee_paise, fee_head.late_fee_cap_paise)
 *   delta_paise    = capped_fee − invoice.late_fee_applied_paise
 * </pre>
 *
 * Only the delta is added each tick — so running the cron twice in the same day is a no-op
 * (matches {@code last_late_fee_applied_at} gating on the query too, as a belt-and-braces).
 *
 * <p>All writes happen in a single transaction per invoice; failures on one invoice don't
 * abort the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LateFeeService {

    private final FeeInvoiceRepository invoiceRepository;
    private final FeeHeadRepository feeHeadRepository;
    private final FeeAdjustmentRepository adjustmentRepository;

    /**
     * Run the late-fee tick for every eligible invoice in the system. Returns a per-school
     * count of invoices touched (helpful for the scheduler's metric publishing).
     */
    public Map<UUID, Integer> applyForAll() {
        LocalDate today = LocalDate.now();
        var invoices = invoiceRepository.findInvoicesEligibleForLateFee(today);
        log.info("Late-fee tick: {} candidate invoices on {}", invoices.size(), today);

        java.util.Map<UUID, Integer> perSchool = new java.util.HashMap<>();
        for (FeeInvoice inv : invoices) {
            try {
                int touched = applyOne(inv, today) ? 1 : 0;
                perSchool.merge(inv.getSchoolId(), touched, Integer::sum);
            } catch (Exception e) {
                log.warn("Late-fee apply failed invoice={} reason={}", inv.getId(), e.getMessage());
            }
        }
        return perSchool;
    }

    /**
     * Apply the late-fee delta to a single invoice. Returns true if any fee was added.
     * Public for unit testing.
     */
    @Transactional
    public boolean applyOne(FeeInvoice inv, LocalDate today) {
        if (inv.getFeeHeadId() == null) return false;     // opening-balance invoices skip
        FeeHead head = feeHeadRepository.findById(inv.getFeeHeadId()).orElse(null);
        if (head == null || head.getLateFeePaisePerDay() <= 0) return false;

        long daysOverdue = Duration.between(
            inv.getDueDate().plusDays(head.getLateFeeGraceDays()).atStartOfDay().toInstant(ZoneOffset.UTC),
            today.atStartOfDay().toInstant(ZoneOffset.UTC)
        ).toDays();

        if (daysOverdue <= 0) return false;

        long rawFee = daysOverdue * head.getLateFeePaisePerDay();
        long cappedFee = head.getLateFeeCapPaise() != null
            ? Math.min(rawFee, head.getLateFeeCapPaise())
            : rawFee;
        long delta = cappedFee - inv.getLateFeeAppliedPaise();

        if (delta <= 0) {
            // Already at cap or earlier tick covered today's amount; just stamp the timestamp.
            inv.setLastLateFeeAppliedAt(OffsetDateTime.now());
            invoiceRepository.save(inv);
            return false;
        }

        // 1. Add the late fee to the invoice.
        inv.setAmountDuePaise(inv.getAmountDuePaise() + delta);
        inv.setLateFeeAppliedPaise(inv.getLateFeeAppliedPaise() + delta);
        inv.setLastLateFeeAppliedAt(OffsetDateTime.now());
        invoiceRepository.save(inv);

        // 2. Record the audit row.
        FeeAdjustment adj = new FeeAdjustment();
        adj.setSchoolId(inv.getSchoolId());
        adj.setInvoiceId(inv.getId());
        adj.setAdjustmentType(FeeAdjustment.AdjustmentType.LATE_FEE);
        adj.setAmountPaise(delta);
        adj.setReason("Auto late fee: " + daysOverdue + " days overdue × ₹"
            + (head.getLateFeePaisePerDay() / 100.0) + "/day");
        adjustmentRepository.save(adj);

        log.debug("Late fee +{}p invoice={} daysOverdue={}", delta, inv.getId(), daysOverdue);
        return true;
    }
}
