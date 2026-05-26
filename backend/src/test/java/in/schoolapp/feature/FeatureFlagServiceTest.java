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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock FeatureRepository featureRepository;
    @Mock FeatureOverrideRepository overrideRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock PlanService planService;

    @InjectMocks FeatureFlagService service;

    UUID schoolId;
    UUID planId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        planId = UUID.randomUUID();
    }

    @Test
    void isEnabled_returnsTrue_whenPlanIncludesFeature_andNoOverride() {
        when(overrideRepository.findBySchoolIdAndFeatureKey(schoolId, FeatureKey.FEE))
            .thenReturn(Optional.empty());
        Subscription sub = subscription(planId);
        when(subscriptionService.findForSchool(schoolId)).thenReturn(Optional.of(sub));
        when(planService.featureKeysForPlan(planId)).thenReturn(Set.of(FeatureKey.FEE));

        assertThat(service.isEnabled(schoolId, FeatureKey.FEE)).isTrue();
    }

    @Test
    void isEnabled_returnsFalse_whenPlanExcludesFeature_andNoOverride() {
        when(overrideRepository.findBySchoolIdAndFeatureKey(schoolId, FeatureKey.PAPER_MIGRATION))
            .thenReturn(Optional.empty());
        when(subscriptionService.findForSchool(schoolId))
            .thenReturn(Optional.of(subscription(planId)));
        when(planService.featureKeysForPlan(planId)).thenReturn(Set.of(FeatureKey.FEE));

        assertThat(service.isEnabled(schoolId, FeatureKey.PAPER_MIGRATION)).isFalse();
    }

    @Test
    void isEnabled_overrideTrumpsPlan_whenOverrideIsTrue() {
        FeatureOverride override = new FeatureOverride();
        override.setSchoolId(schoolId);
        override.setFeatureKey(FeatureKey.PAPER_MIGRATION);
        override.setEnabled(true);
        when(overrideRepository.findBySchoolIdAndFeatureKey(schoolId, FeatureKey.PAPER_MIGRATION))
            .thenReturn(Optional.of(override));

        // Plan would NOT include it, but the override wins.
        assertThat(service.isEnabled(schoolId, FeatureKey.PAPER_MIGRATION)).isTrue();
    }

    @Test
    void isEnabled_overrideTrumpsPlan_whenOverrideIsFalse() {
        FeatureOverride override = new FeatureOverride();
        override.setSchoolId(schoolId);
        override.setFeatureKey(FeatureKey.FEE);
        override.setEnabled(false);
        when(overrideRepository.findBySchoolIdAndFeatureKey(schoolId, FeatureKey.FEE))
            .thenReturn(Optional.of(override));

        assertThat(service.isEnabled(schoolId, FeatureKey.FEE)).isFalse();
    }

    @Test
    void isEnabled_returnsFalse_whenNoSubscription_andNoOverride() {
        when(overrideRepository.findBySchoolIdAndFeatureKey(schoolId, FeatureKey.FEE))
            .thenReturn(Optional.empty());
        when(subscriptionService.findForSchool(schoolId)).thenReturn(Optional.empty());

        assertThat(service.isEnabled(schoolId, FeatureKey.FEE)).isFalse();
    }

    @Test
    void enforceEnabled_throwsFeatureDisabled_whenOff() {
        when(overrideRepository.findBySchoolIdAndFeatureKey(any(), any()))
            .thenReturn(Optional.empty());
        when(subscriptionService.findForSchool(schoolId))
            .thenReturn(Optional.of(subscription(planId)));
        when(planService.featureKeysForPlan(planId)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.enforceEnabled(schoolId, FeatureKey.FEE))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.FEATURE_DISABLED);
    }

    @Test
    void setOverride_throwsWhenFeatureKeyUnknown() {
        when(featureRepository.findById("MADE_UP_FEATURE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setOverride(schoolId, "MADE_UP_FEATURE", true, null, null))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void setOverride_createsRowAndSaves() {
        Feature f = new Feature();
        f.setFeatureKey(FeatureKey.PAPER_MIGRATION);
        when(featureRepository.findById(FeatureKey.PAPER_MIGRATION)).thenReturn(Optional.of(f));
        when(overrideRepository.findBySchoolIdAndFeatureKey(schoolId, FeatureKey.PAPER_MIGRATION))
            .thenReturn(Optional.empty());
        when(overrideRepository.save(any(FeatureOverride.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        FeatureOverride out = service.setOverride(schoolId, FeatureKey.PAPER_MIGRATION, true,
            "Pilot customer", UUID.randomUUID());

        assertThat(out.getFeatureKey()).isEqualTo(FeatureKey.PAPER_MIGRATION);
        assertThat(out.isEnabled()).isTrue();
        assertThat(out.getNote()).isEqualTo("Pilot customer");
    }

    // --- helper ---
    private Subscription subscription(UUID planId) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setSchoolId(schoolId);
        s.setPlanId(planId);
        return s;
    }
}
