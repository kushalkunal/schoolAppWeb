package in.schoolapp.billing;

import in.schoolapp.billing.entity.Plan;
import in.schoolapp.billing.entity.PlanLimit;
import in.schoolapp.billing.repository.PlanFeatureRepository;
import in.schoolapp.billing.repository.PlanLimitRepository;
import in.schoolapp.billing.repository.PlanRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only access to the plans / features / limits catalog. Cached at the call site —
 * the catalog changes rarely; an extra DB hit per uncached request is acceptable for now.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanLimitRepository planLimitRepository;

    public Plan getPlan(UUID planId) {
        return planRepository.findById(planId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Plan", planId));
    }

    public Plan getPlanByCode(String code) {
        return planRepository.findByCode(code)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                "Plan with code '" + code + "' not found"));
    }

    public List<Plan> listActivePlans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public Set<String> featureKeysForPlan(UUID planId) {
        return planFeatureRepository.findFeatureKeysByPlanId(planId);
    }

    /**
     * Returns the plan's ceiling for the given metric. {@code null} means "no limit row
     * exists" — callers should treat that as unlimited (defensive default; in practice
     * Flyway V6 seeds limits for every plan × metric pair).
     */
    public Long limitFor(UUID planId, String metric) {
        return planLimitRepository.findById_PlanIdAndId_Metric(planId, metric)
            .map(PlanLimit::getLimitValue)
            .orElse(null);
    }
}
