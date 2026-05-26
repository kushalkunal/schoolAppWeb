package in.schoolapp.feature;

import in.schoolapp.billing.PlanService;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.feature.entity.Feature;
import in.schoolapp.feature.entity.FeatureOverride;
import in.schoolapp.feature.repository.FeatureOverrideRepository;
import in.schoolapp.feature.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the effective enabled-state of a feature for a school:
 * <ol>
 *   <li>If a {@link FeatureOverride} row exists for {@code (schoolId, featureKey)} →
 *       use its {@code enabled} value (overrides plan).</li>
 *   <li>Else look up the school's subscription plan and check {@code plan_features} →
 *       enabled iff the plan includes the key.</li>
 * </ol>
 *
 * <p>Cached via Spring Cache (keyed on schoolId+featureKey) so the hot path is a single
 * Redis GET. Cache entries are invalidated when an override is written.
 *
 * <p>Cache backend is the Spring default (in-memory) until {@code RedisConfig} wires Redis as
 * the cache manager — works either way; the keys + invalidation strategy are correct.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    /** Spring Cache region for resolved flags. Externalised to a constant for tests. */
    public static final String CACHE_NAME = "featureFlags";

    private final FeatureRepository featureRepository;
    private final FeatureOverrideRepository overrideRepository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;

    /**
     * Returns true iff the feature is enabled for this school. Never throws; an unknown
     * featureKey just returns false (graceful degradation if a constant gets removed before
     * its DB row is cleaned up).
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "#schoolId + ':' + #featureKey",
               unless = "#result == null")
    public boolean isEnabled(UUID schoolId, String featureKey) {
        if (schoolId == null || featureKey == null) return false;

        // 1. Override wins if present.
        var override = overrideRepository.findBySchoolIdAndFeatureKey(schoolId, featureKey);
        if (override.isPresent()) {
            return override.get().isEnabled();
        }

        // 2. Fall back to the school's plan's feature set.
        return subscriptionService.findForSchool(schoolId)
            .map(Subscription::getPlanId)
            .map(planId -> planService.featureKeysForPlan(planId).contains(featureKey))
            .orElse(false);
    }

    /** Bulk read of effective state for one school. Convenient for the platform-admin UI. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> effectiveFlagsForSchool(UUID schoolId) {
        // All known feature keys.
        Set<String> allKeys = new HashSet<>();
        featureRepository.findAll().forEach(f -> allKeys.add(f.getFeatureKey()));

        // Plan-included keys (default).
        Set<String> planKeys = subscriptionService.findForSchool(schoolId)
            .map(Subscription::getPlanId)
            .map(planService::featureKeysForPlan)
            .orElse(Set.of());

        // Override snapshot.
        Map<String, Boolean> overrides = new HashMap<>();
        for (FeatureOverride ov : overrideRepository.findBySchoolId(schoolId)) {
            overrides.put(ov.getFeatureKey(), ov.isEnabled());
        }

        Map<String, Boolean> out = new HashMap<>();
        for (String key : allKeys) {
            out.put(key, overrides.getOrDefault(key, planKeys.contains(key)));
        }
        return out;
    }

    /**
     * Enforce + throw form. Used by the {@code @RequiresFeature} aspect.
     */
    public void enforceEnabled(UUID schoolId, String featureKey) {
        if (!isEnabled(schoolId, featureKey)) {
            throw new AppException(ErrorCode.FEATURE_DISABLED,
                "Feature '" + featureKey + "' is not enabled for this school. "
                + "Upgrade the plan or contact platform admin.",
                Map.of("featureKey", featureKey));
        }
    }

    /**
     * Upserts a per-school override and evicts the cached entry. Visible to platform admins
     * (via PlatformAdminController) and — in a later slice — to school owners.
     */
    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#schoolId + ':' + #featureKey")
    public FeatureOverride setOverride(UUID schoolId, String featureKey, boolean enabled,
                                       String note, UUID updatedByStaffId) {
        // Sanity check: feature must exist in catalog (FK enforces too, but a clean error helps).
        Feature feature = featureRepository.findById(featureKey)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                "Feature '" + featureKey + "' is not in the catalog"));

        FeatureOverride row = overrideRepository.findBySchoolIdAndFeatureKey(schoolId, featureKey)
            .orElseGet(() -> {
                FeatureOverride o = new FeatureOverride();
                o.setSchoolId(schoolId);
                o.setFeatureKey(feature.getFeatureKey());
                o.setConfig(new HashMap<>());
                return o;
            });
        row.setEnabled(enabled);
        row.setNote(note);
        row.setUpdatedById(updatedByStaffId);
        FeatureOverride saved = overrideRepository.save(row);
        log.info("Set feature override school={} key={} enabled={} actor={}",
            schoolId, featureKey, enabled, updatedByStaffId);
        return saved;
    }

    /**
     * Clears a per-school override so the school falls back to its plan default.
     */
    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#schoolId + ':' + #featureKey")
    public void clearOverride(UUID schoolId, String featureKey) {
        overrideRepository.findBySchoolIdAndFeatureKey(schoolId, featureKey)
            .ifPresent(overrideRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<Feature> catalog() {
        return featureRepository.findAll();
    }
}
