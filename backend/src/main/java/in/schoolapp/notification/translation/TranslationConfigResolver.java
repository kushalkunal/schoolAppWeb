package in.schoolapp.notification.translation;

import in.schoolapp.notification.translation.config.TranslationProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final TranslationProperties globalProps;

    public Optional<ResolvedTranslation> resolve(UUID schoolId) {
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.TRANSLATION);
            if (perTenant.isPresent()) {
                String provider = perTenant.get().provider();
                if (ProviderType.LOGGING.equals(provider)) return Optional.empty();
                Object key = perTenant.get().config().get("api_key");
                if (key instanceof String s && !s.isBlank()) {
                    Object model = perTenant.get().config().get("model");
                    return Optional.of(new ResolvedTranslation(
                        provider, s,
                        model instanceof String m ? m : null,
                        perTenant.get().id()));
                }
                log.warn("Tenant {} translation config missing api_key", schoolId);
            }
        }

        if (globalProps != null) {
            String provider = globalProps.provider();
            if ("GOOGLE_TRANSLATE".equals(provider) && globalProps.google() != null
                && isNonBlank(globalProps.google().apiKey())) {
                return Optional.of(new ResolvedTranslation(
                    ProviderType.GOOGLE_TRANSLATE, globalProps.google().apiKey(), null, null));
            }
            if ("DEEPL".equals(provider) && globalProps.deepl() != null
                && isNonBlank(globalProps.deepl().apiKey())) {
                return Optional.of(new ResolvedTranslation(
                    ProviderType.DEEPL, globalProps.deepl().apiKey(),
                    globalProps.deepl().freeTier() ? "free" : "pro", null));
            }
            if ("OPENAI".equals(provider) && globalProps.openai() != null
                && isNonBlank(globalProps.openai().apiKey())) {
                return Optional.of(new ResolvedTranslation(
                    ProviderType.OPENAI, globalProps.openai().apiKey(),
                    globalProps.openai().model() != null ? globalProps.openai().model() : "gpt-4o-mini",
                    null));
            }
        }
        return Optional.empty();
    }

    private static boolean isNonBlank(String s) { return s != null && !s.isBlank(); }

    public record ResolvedTranslation(String provider, String apiKey, String modelOrTier, UUID configId) {}
}
