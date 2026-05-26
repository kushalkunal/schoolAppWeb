package in.schoolapp.billing.usage;

import in.schoolapp.billing.PlanService;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.usage.entity.UsageCounter;
import in.schoolapp.billing.usage.repository.UsageCounterRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tenant usage counters with plan-limit enforcement.
 *
 * <p>Writes use a Postgres {@code ON CONFLICT DO UPDATE} upsert via a native query so two
 * concurrent increments for the same counter compose correctly without explicit row locking.
 *
 * <p>{@link #enforceLimit(UUID, UsageMetric)} reads the school's plan limit and the current
 * counter; if at-or-over, throws {@code PLAN_LIMIT_EXCEEDED}. Callers should call this <em>before</em>
 * the work that increments the counter (i.e. on the write path, not the read path).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    public static final String PERIOD_KEY_ALL = "ALL";

    private final UsageCounterRepository repository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;

    /**
     * Best-effort increment. Wraps in {@code REQUIRES_NEW} so a downstream caller's rollback
     * does not erase the counter — usage metering is independent of business write success
     * for monitoring purposes. Where strict accuracy matters (limit pre-check), call
     * {@link #enforceLimit(UUID, UsageMetric)} first.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(UUID schoolId, UsageMetric metric, long delta) {
        if (schoolId == null || delta == 0) return;
        try {
            repository.atomicIncrement(schoolId, metric.name(), periodKey(metric), delta);
        } catch (Exception e) {
            // Never let a counter failure break a business flow.
            log.warn("Usage counter increment failed school={} metric={} delta={} : {}",
                schoolId, metric, delta, e.toString());
        }
    }

    @Transactional(readOnly = true)
    public long currentValue(UUID schoolId, UsageMetric metric) {
        return repository.findById(new UsageCounter.Id(schoolId, metric.name(), periodKey(metric)))
            .map(UsageCounter::getCountValue)
            .orElse(0L);
    }

    /**
     * Throws {@link AppException} with {@code PLAN_LIMIT_EXCEEDED} when the school is at or
     * above its plan limit for the given metric. {@code limit == -1} means unlimited and is
     * always allowed.
     */
    @Transactional(readOnly = true)
    public void enforceLimit(UUID schoolId, UsageMetric metric) {
        Subscription sub = subscriptionService.getForSchool(schoolId);
        Long limit = planService.limitFor(sub.getPlanId(), metric.name());
        if (limit == null || limit < 0) return;  // no row or sentinel -1 = unlimited
        long current = currentValue(schoolId, metric);
        if (current >= limit) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED,
                "Plan limit reached for " + metric + ": " + current + " / " + limit
                    + ". Upgrade plan to continue.",
                Map.of("metric", metric.name(), "limit", limit, "current", current));
        }
    }

    /** Snapshot for the platform-admin UI. */
    @Transactional(readOnly = true)
    public Map<String, Long> snapshot(UUID schoolId) {
        Map<String, Long> out = new HashMap<>();
        for (UsageMetric m : UsageMetric.values()) {
            out.put(m.name(), currentValue(schoolId, m));
        }
        return out;
    }

    /** Computes the appropriate period_key for this metric, in UTC. */
    public static String periodKey(UsageMetric metric) {
        return switch (metric.granularity()) {
            case MONTHLY  -> YearMonth.now(ZoneOffset.UTC).toString();   // "YYYY-MM"
            case ALL_TIME -> PERIOD_KEY_ALL;
        };
    }
}
