package in.schoolapp.platform;

import in.schoolapp.billing.PlanService;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.billing.entity.Plan;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.TenantContext;
import in.schoolapp.feature.FeatureFlagService;
import in.schoolapp.feature.entity.Feature;
import in.schoolapp.feature.entity.FeatureOverride;
import in.schoolapp.platform.dto.ChangePlanRequest;
import in.schoolapp.platform.dto.FeatureOverrideRequest;
import in.schoolapp.platform.dto.SuspendRequest;
import in.schoolapp.platform.dto.TenantDetail;
import in.schoolapp.platform.dto.TenantSummary;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderVerificationService;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import in.schoolapp.tenantconfig.dto.SetProviderConfigRequest;
import in.schoolapp.tenantconfig.entity.TenantProviderConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * SUPER_ADMIN-only endpoints for managing tenants across the platform.
 *
 * <p>Path prefix {@code /api/v1/platform/**}. Excluded from both {@code TenantInterceptor}
 * (these endpoints span tenants) and {@code SubscriptionGuardInterceptor} (they're how a
 * suspended tenant gets reactivated). Access enforced via {@code hasRole('SUPER_ADMIN')}.
 */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final FeatureFlagService featureFlagService;
    private final PlanService planService;
    private final TenantProviderConfigService providerConfigService;
    private final ProviderVerificationService providerVerificationService;

    // ---------- Tenant browsing ----------

    @GetMapping("/tenants")
    public ApiResponse<List<TenantSummary>> listTenants(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Page<TenantSummary> p = platformAdminService.listTenants(page, size);
        return ApiResponse.success(
            p.getContent(),
            new ApiResponse.Meta(p.getTotalElements(), p.getNumber(), p.getSize(), null)
        );
    }

    @GetMapping("/tenants/{schoolId}")
    public ApiResponse<TenantDetail> getTenant(@PathVariable UUID schoolId) {
        return ApiResponse.success(platformAdminService.getDetail(schoolId));
    }

    @GetMapping("/tenants/{schoolId}/subscription/events")
    public ApiResponse<List<SubscriptionEvent>> events(@PathVariable UUID schoolId) {
        return ApiResponse.success(
            subscriptionEventRepository.findBySchoolIdOrderByCreatedAtDesc(schoolId));
    }

    // ---------- Subscription mutations ----------

    @PutMapping("/tenants/{schoolId}/subscription")
    public ApiResponse<Subscription> changePlan(@PathVariable UUID schoolId,
                                                @Valid @RequestBody ChangePlanRequest req) {
        return ApiResponse.success(
            subscriptionService.changePlan(schoolId, req.planCode(), req.note()));
    }

    @PostMapping("/tenants/{schoolId}/suspend")
    public ApiResponse<Subscription> suspend(@PathVariable UUID schoolId,
                                             @Valid @RequestBody SuspendRequest req) {
        return ApiResponse.success(subscriptionService.suspend(schoolId, req.reason()));
    }

    @PostMapping("/tenants/{schoolId}/resume")
    public ApiResponse<Subscription> resume(@PathVariable UUID schoolId) {
        return ApiResponse.success(subscriptionService.resume(schoolId, "Resumed by platform admin"));
    }

    @PostMapping("/tenants/{schoolId}/cancel")
    public ApiResponse<Subscription> cancel(@PathVariable UUID schoolId,
                                            @Valid @RequestBody SuspendRequest req) {
        return ApiResponse.success(subscriptionService.cancel(schoolId, req.reason()));
    }

    // ---------- Feature overrides ----------

    @PutMapping("/tenants/{schoolId}/features/{featureKey}")
    public ApiResponse<FeatureOverride> setFeatureOverride(
        @PathVariable UUID schoolId,
        @PathVariable String featureKey,
        @Valid @RequestBody FeatureOverrideRequest req
    ) {
        return ApiResponse.success(featureFlagService.setOverride(
            schoolId, featureKey, req.enabled(), req.note(), TenantContext.getStaffId()));
    }

    @DeleteMapping("/tenants/{schoolId}/features/{featureKey}")
    public ApiResponse<Void> clearFeatureOverride(@PathVariable UUID schoolId,
                                                  @PathVariable String featureKey) {
        featureFlagService.clearOverride(schoolId, featureKey);
        return ApiResponse.ok();
    }

    // ---------- Catalogs ----------

    @GetMapping("/plans")
    public ApiResponse<List<Plan>> listPlans() {
        return ApiResponse.success(planService.listActivePlans());
    }

    @GetMapping("/features")
    public ApiResponse<List<Feature>> listFeatures() {
        return ApiResponse.success(featureFlagService.catalog());
    }

    // ---------- Per-tenant provider configs (slice 2) ----------

    /**
     * Lists every concern's config for a tenant. Decrypted secrets are returned because
     * the caller is SUPER_ADMIN — they're the operator who set the credentials in the first
     * place. School-owner self-view uses the masked endpoint on the tenant controller.
     */
    @GetMapping("/tenants/{schoolId}/providers")
    public ApiResponse<List<TenantProviderConfig>> listProviders(@PathVariable UUID schoolId) {
        return ApiResponse.success(providerConfigService.listForTenant(schoolId));
    }

    @PutMapping("/tenants/{schoolId}/providers/{concern}")
    public ApiResponse<TenantProviderConfig> setProvider(
        @PathVariable UUID schoolId,
        @PathVariable ProviderConcern concern,
        @Valid @RequestBody SetProviderConfigRequest req
    ) {
        return ApiResponse.success(providerConfigService.set(
            schoolId, concern, req.provider(), req.config(), req.note(),
            TenantContext.getStaffId()));
    }

    @DeleteMapping("/tenants/{schoolId}/providers/{concern}")
    public ApiResponse<Void> clearProvider(@PathVariable UUID schoolId,
                                           @PathVariable ProviderConcern concern) {
        providerConfigService.clear(schoolId, concern);
        return ApiResponse.ok();
    }

    /**
     * Round-trips the configured external provider to confirm credentials are valid.
     * Stamps {@code verified_at} on success or {@code last_error} on failure. Slice 3 wires
     * a real probe for WHATSAPP+WATI; other concerns return a "not yet verifiable" result.
     */
    @PostMapping("/tenants/{schoolId}/providers/{concern}/verify")
    public ApiResponse<ProviderVerificationService.VerifyResult> verifyProvider(
        @PathVariable UUID schoolId,
        @PathVariable ProviderConcern concern
    ) {
        return ApiResponse.success(providerVerificationService.verify(schoolId, concern));
    }
}
