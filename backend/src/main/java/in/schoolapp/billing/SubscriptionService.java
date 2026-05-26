package in.schoolapp.billing;

import in.schoolapp.billing.entity.Plan;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.billing.repository.SubscriptionRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription lifecycle: start trial (called from signup), change plan, suspend, resume,
 * cancel. Every transition writes a {@link SubscriptionEvent} for audit.
 * <p>
 * Looks up the school's current subscription on every read — cached neither in-memory nor in
 * Redis at this layer, because the suspension guard runs once per request and we want the
 * latest status. (FeatureFlagService caches; subscription state does not.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final PlanService planService;

    @Value("${app.billing.trial-days:14}")
    private int trialDays;

    @Value("${app.billing.default-plan-code:FREE}")
    private String defaultPlanCode;

    /**
     * Creates a fresh subscription on the default plan (FREE) in TRIAL status. Called by
     * SchoolService.createSchool after the school row is committed.
     */
    @Transactional
    public Subscription startTrialForSchool(UUID schoolId) {
        if (subscriptionRepository.findBySchoolId(schoolId).isPresent()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Subscription already exists for school " + schoolId);
        }
        Plan plan = planService.getPlanByCode(defaultPlanCode);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime trialEnd = now.plusDays(trialDays);

        Subscription sub = new Subscription();
        sub.setSchoolId(schoolId);
        sub.setPlanId(plan.getId());
        sub.setStatus(SubscriptionStatus.TRIAL);
        sub.setTrialEndsAt(trialEnd);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(trialEnd);
        sub = subscriptionRepository.save(sub);

        writeEvent(sub, "TRIAL_STARTED", null, plan.getCode(), null, "TRIAL",
            "Trial started — " + trialDays + " days");
        log.info("Started trial subscription school={} plan={} ends={}",
            schoolId, plan.getCode(), trialEnd);
        return sub;
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findForSchool(UUID schoolId) {
        return subscriptionRepository.findBySchoolId(schoolId);
    }

    @Transactional(readOnly = true)
    public Subscription getForSchool(UUID schoolId) {
        return findForSchool(schoolId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                "No subscription for school " + schoolId));
    }

    /** Effective status — currently just the stored status; later may consider trial expiry. */
    @Transactional(readOnly = true)
    public SubscriptionStatus effectiveStatus(UUID schoolId) {
        return findForSchool(schoolId)
            .map(Subscription::getStatus)
            // No row = treat as suspended (defensive — should never happen post-V6 backfill).
            .orElse(SubscriptionStatus.SUSPENDED);
    }

    @Transactional
    public Subscription changePlan(UUID schoolId, String newPlanCode, String note) {
        Subscription sub = getForSchool(schoolId);
        Plan oldPlan = planService.getPlan(sub.getPlanId());
        if (oldPlan.getCode().equals(newPlanCode)) {
            return sub; // no-op
        }
        Plan newPlan = planService.getPlanByCode(newPlanCode);
        sub.setPlanId(newPlan.getId());
        // Moving from a SUSPENDED state via a plan-change implies the operator is reactivating.
        if (sub.getStatus() == SubscriptionStatus.SUSPENDED
                || sub.getStatus() == SubscriptionStatus.CANCELLED) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setSuspensionReason(null);
            sub.setCancelledAt(null);
        }
        sub = subscriptionRepository.save(sub);
        writeEvent(sub, "PLAN_CHANGED", oldPlan.getCode(), newPlan.getCode(),
            sub.getStatus().name(), sub.getStatus().name(), note);
        log.info("Changed plan school={} from={} to={}",
            schoolId, oldPlan.getCode(), newPlan.getCode());
        return sub;
    }

    @Transactional
    public Subscription suspend(UUID schoolId, String reason) {
        Subscription sub = getForSchool(schoolId);
        if (sub.getStatus() == SubscriptionStatus.SUSPENDED) {
            return sub;
        }
        String from = sub.getStatus().name();
        sub.setStatus(SubscriptionStatus.SUSPENDED);
        sub.setSuspensionReason(reason);
        sub = subscriptionRepository.save(sub);
        writeEvent(sub, "SUSPENDED", null, null, from, "SUSPENDED", reason);
        log.warn("Suspended subscription school={} reason={}", schoolId, reason);
        return sub;
    }

    @Transactional
    public Subscription resume(UUID schoolId, String note) {
        Subscription sub = getForSchool(schoolId);
        if (sub.getStatus() == SubscriptionStatus.ACTIVE
                || sub.getStatus() == SubscriptionStatus.TRIAL) {
            return sub;
        }
        String from = sub.getStatus().name();
        // Resume to TRIAL if the trial window is still in the future; else ACTIVE.
        boolean inTrial = sub.getTrialEndsAt() != null
            && sub.getTrialEndsAt().isAfter(OffsetDateTime.now());
        sub.setStatus(inTrial ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE);
        sub.setSuspensionReason(null);
        sub = subscriptionRepository.save(sub);
        writeEvent(sub, "RESUMED", null, null, from, sub.getStatus().name(), note);
        log.info("Resumed subscription school={} to={}", schoolId, sub.getStatus());
        return sub;
    }

    @Transactional
    public Subscription cancel(UUID schoolId, String reason) {
        Subscription sub = getForSchool(schoolId);
        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            return sub;
        }
        String from = sub.getStatus().name();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(OffsetDateTime.now());
        sub.setSuspensionReason(reason);
        sub = subscriptionRepository.save(sub);
        writeEvent(sub, "CANCELLED", null, null, from, "CANCELLED", reason);
        log.warn("Cancelled subscription school={} reason={}", schoolId, reason);
        return sub;
    }

    private void writeEvent(Subscription sub, String eventType, String fromPlan, String toPlan,
                            String fromStatus, String toStatus, String note) {
        SubscriptionEvent e = new SubscriptionEvent();
        e.setSchoolId(sub.getSchoolId());
        e.setSubscriptionId(sub.getId());
        e.setEventType(eventType);
        e.setFromPlanCode(fromPlan);
        e.setToPlanCode(toPlan);
        e.setFromStatus(fromStatus);
        e.setToStatus(toStatus);
        e.setActorStaffId(TenantContext.getStaffId());  // null for system events — fine
        e.setNote(note);
        subscriptionEventRepository.save(e);
    }
}
