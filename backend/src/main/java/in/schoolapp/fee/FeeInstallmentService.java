package in.schoolapp.fee;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.dto.InstallmentPlanRequest;
import in.schoolapp.fee.dto.InstallmentPlanResponse;
import in.schoolapp.fee.entity.FeeInstallment;
import in.schoolapp.fee.entity.FeeInstallmentPlan;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import in.schoolapp.fee.repository.FeeInstallmentPlanRepository;
import in.schoolapp.fee.repository.FeeInstallmentRepository;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Splits one parent invoice into N child invoices. The parent flips to
 * {@link InvoiceStatus#SUPERSEDED} so existing dashboards/defaulters queries (which already
 * filter by status) ignore it. Children inherit the parent's student, fee head, academic
 * year and description so receipts and reminders continue to work unchanged.
 *
 * <p>Validation:
 * <ul>
 *   <li>Parent must be PENDING (no part-payment already applied).</li>
 *   <li>Sum of installments must equal the parent's outstanding balance.</li>
 *   <li>At least two installments — otherwise this would be a no-op.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeInstallmentService {

    private final FeeInvoiceRepository invoiceRepository;
    private final FeeInstallmentPlanRepository planRepository;
    private final FeeInstallmentRepository installmentRepository;

    @Transactional
    public InstallmentPlanResponse splitInvoice(UUID tenantId, UUID parentInvoiceId, InstallmentPlanRequest req) {
        FeeInvoice parent = invoiceRepository.findByIdAndSchoolId(parentInvoiceId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Invoice not found"));

        if (parent.getStatus() != InvoiceStatus.PENDING) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Only PENDING invoices can be split (this one is " + parent.getStatus() + ")");
        }
        if (req.installments() == null || req.installments().size() < 2) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "At least 2 installments required");
        }
        long sum = req.installments().stream()
            .mapToLong(InstallmentPlanRequest.Installment::amountPaise).sum();
        if (sum != parent.getAmountDuePaise()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Installments sum (" + sum + ") must equal parent invoice amount ("
                + parent.getAmountDuePaise() + ")");
        }

        if (planRepository.findByParentInvoiceId(parentInvoiceId).isPresent()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "This invoice already has an installment plan");
        }

        // 1. Create the plan.
        FeeInstallmentPlan plan = new FeeInstallmentPlan();
        plan.setSchoolId(tenantId);
        plan.setParentInvoiceId(parentInvoiceId);
        plan.setInstallmentCount(req.installments().size());
        plan.setNotes(req.notes());
        plan = planRepository.save(plan);

        // 2. Generate one child invoice per installment.
        List<UUID> childIds = new ArrayList<>(req.installments().size());
        for (int i = 0; i < req.installments().size(); i++) {
            var spec = req.installments().get(i);

            FeeInvoice child = new FeeInvoice();
            child.setSchoolId(parent.getSchoolId());
            child.setStudentId(parent.getStudentId());
            child.setFeeHeadId(parent.getFeeHeadId());
            child.setAmountDuePaise(spec.amountPaise());
            child.setAmountPaidPaise(0);
            child.setDueDate(spec.dueDate());
            child.setStatus(InvoiceStatus.PENDING);
            child.setAcademicYearId(parent.getAcademicYearId());
            child.setDescription((parent.getDescription() != null ? parent.getDescription() : "Fee")
                + " — installment " + (i + 1) + "/" + req.installments().size());
            child = invoiceRepository.save(child);

            FeeInstallment row = new FeeInstallment();
            row.setPlanId(plan.getId());
            row.setChildInvoiceId(child.getId());
            row.setSequenceNo(i + 1);
            row.setDueDate(spec.dueDate());
            row.setAmountPaise(spec.amountPaise());
            installmentRepository.save(row);

            childIds.add(child.getId());
        }

        // 3. Mark parent SUPERSEDED.
        parent.setStatus(InvoiceStatus.SUPERSEDED);
        parent.setSupersededByPlanId(plan.getId());
        invoiceRepository.save(parent);

        log.info("Installment plan tenant={} parent={} children={}", tenantId, parentInvoiceId, childIds.size());
        return new InstallmentPlanResponse(plan.getId(), parentInvoiceId, childIds, plan.getCreatedAt());
    }
}
