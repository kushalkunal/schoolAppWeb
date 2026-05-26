package in.schoolapp.notification.push;

import in.schoolapp.notification.push.config.PushProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves effective push credentials per send. Tenant config → env fallback → empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final PushProperties globalProps;

    public Optional<ResolvedPush> resolve(UUID schoolId) {
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.PUSH);
            if (perTenant.isPresent()) {
                String provider = perTenant.get().provider();
                if (ProviderType.LOGGING.equals(provider)) return Optional.empty();
                if (ProviderType.FCM.equals(provider)) {
                    Object pid = perTenant.get().config().get("project_id");
                    Object sa  = perTenant.get().config().get("service_account_json");
                    if (allStrings(pid, sa)) {
                        return Optional.of(new ResolvedPush(
                            ProviderType.FCM,
                            new FcmCreds((String) pid, (String) sa),
                            null,
                            perTenant.get().id()));
                    }
                    log.warn("Tenant {} FCM row missing project_id or service_account_json", schoolId);
                }
                // APNS routing — left for a follow-up.
            }
        }

        if (globalProps != null && "FCM".equals(globalProps.provider())
            && globalProps.fcm() != null
            && isNonBlank(globalProps.fcm().projectId())
            && isNonBlank(globalProps.fcm().serviceAccountJson())) {
            return Optional.of(new ResolvedPush(
                ProviderType.FCM,
                new FcmCreds(globalProps.fcm().projectId(), globalProps.fcm().serviceAccountJson()),
                null,
                null));
        }
        return Optional.empty();
    }

    private static boolean allStrings(Object... values) {
        for (Object v : values) if (!(v instanceof String s) || s.isBlank()) return false;
        return true;
    }

    private static boolean isNonBlank(String s) { return s != null && !s.isBlank(); }

    public record ResolvedPush(String provider, FcmCreds fcm, ApnsCreds apns, UUID configId) {}
    public record FcmCreds(String projectId, String serviceAccountJson) {}
    public record ApnsCreds(String teamId, String keyId, String bundleId, String privateKey) {}
}
