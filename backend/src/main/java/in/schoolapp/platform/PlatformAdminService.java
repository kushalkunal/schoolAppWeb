package in.schoolapp.platform;

import in.schoolapp.billing.PlanService;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.billing.entity.Plan;
import in.schoolapp.billing.entity.PlanLimit;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.repository.PlanLimitRepository;
import in.schoolapp.billing.usage.UsageMetric;
import in.schoolapp.billing.usage.UsageService;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.feature.FeatureFlagService;
import in.schoolapp.platform.dto.TenantDetail;
import in.schoolapp.platform.dto.TenantSummary;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side composition for the platform-admin endpoints. Write-side operations delegate to
 * {@link SubscriptionService} and {@link FeatureFlagService} so the same audit + cache-evict
 * paths run whether a school owner or a platform admin makes the change.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final SchoolRepository schoolRepository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;
    private final PlanLimitRepository planLimitRepository;
    private final UsageService usageService;
    private final FeatureFlagService featureFlagService;
    @SuppressWarnings("unused")
    private final SchoolService schoolService;   // reserved for future deeper lookups

    @Transactional(readOnly = true)
    public Page<TenantSummary> listTenants(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
        return schoolRepository.findAll(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public TenantDetail getDetail(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.SCHOOL_NOT_FOUND, "School", schoolId));
        TenantSummary summary = toSummary(school);

        Map<String, Long> limits = new HashMap<>();
        if (summary.subscriptionId() != null) {
            UUID planId = subscriptionService.getForSchool(schoolId).getPlanId();
            for (PlanLimit pl : planLimitRepository.findById_PlanId(planId)) {
                limits.put(pl.getId().getMetric(), pl.getLimitValue());
            }
        }

        return new TenantDetail(
            summary,
            featureFlagService.effectiveFlagsForSchool(schoolId),
            usageService.snapshot(schoolId),
            limits
        );
    }

    /**
     * Builds a summary from a School row by re-looking up the subscription. O(1) per row;
     * acceptable for paged lists in slice 1. If listing becomes hot we can pull subscriptions
     * in a single query keyed by the page's school ids.
     */
    private TenantSummary toSummary(School school) {
        return subscriptionService.findForSchool(school.getId())
            .map(sub -> {
                String planCode = planService.getPlan(sub.getPlanId()).getCode();
                return TenantSummary.of(school, sub, planCode);
            })
            .orElseGet(() -> TenantSummary.of(school, null, null));
    }

    public java.util.List<Plan> listActivePlans() {
        return planService.listActivePlans();
    }
}
