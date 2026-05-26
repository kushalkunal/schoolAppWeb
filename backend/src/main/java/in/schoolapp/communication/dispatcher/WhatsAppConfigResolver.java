package in.schoolapp.communication.dispatcher;

import in.schoolapp.communication.dispatcher.config.WhatsAppProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the effective WhatsApp configuration for an outbound send.
 *
 * <p>Resolution order:
 * <ol>
 *   <li><strong>Per-tenant DB row</strong> — {@code tenant_provider_configs} entry for
 *       {@code (schoolId, WHATSAPP)} with provider {@code WATI}.</li>
 *   <li><strong>JVM-global env vars</strong> — {@code app.whatsapp.wati.base-url} +
 *       {@code app.whatsapp.wati.token}. Used as the deployment-wide fallback when a tenant
 *       hasn't configured their own credentials.</li>
 *   <li><strong>Empty</strong> — caller should degrade (log + skip, or use LOGGING notifier).</li>
 * </ol>
 *
 * <p>Per-tenant config provider {@code LOGGING} returns empty (the caller treats this as
 * "intentionally muted" — different from "not configured"). This lets a school turn off
 * WhatsApp without involving platform support.
 *
 * <p>Note: returned {@link WatiCreds} carries the DECRYPTED token. Never log or expose it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final WhatsAppProperties globalProps;

    public Optional<WatiCreds> resolve(UUID schoolId) {
        // 1. Per-tenant config wins. Read using getActive (DECRYPTED).
        if (schoolId != null) {
            Optional<TenantProviderConfigService.ResolvedConfig> perTenant =
                tenantConfigService.getActive(schoolId, ProviderConcern.WHATSAPP);
            if (perTenant.isPresent()) {
                String provider = perTenant.get().provider();
                if (ProviderType.LOGGING.equals(provider)) {
                    // Tenant explicitly chose to mute outbound WhatsApp.
                    return Optional.empty();
                }
                if (ProviderType.WATI.equals(provider)) {
                    Object baseUrl = perTenant.get().config().get("base_url");
                    Object token   = perTenant.get().config().get("token");
                    if (isString(baseUrl) && isString(token)) {
                        return Optional.of(new WatiCreds(
                            (String) baseUrl, (String) token, perTenant.get().id()));
                    }
                    log.warn("Tenant {} has WATI config row but base_url/token are missing/blank",
                        schoolId);
                }
                // Other WhatsApp providers (TWILIO_WA, INTERAKT) — not yet routed.
            }
        }

        // 2. JVM-global env fallback.
        if (globalProps.wati() != null
            && isNonBlank(globalProps.wati().baseUrl())
            && isNonBlank(globalProps.wati().token())) {
            return Optional.of(new WatiCreds(
                globalProps.wati().baseUrl(), globalProps.wati().token(), null));
        }

        return Optional.empty();
    }

    private static boolean isString(Object v) {
        return v instanceof String s && !s.isBlank();
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Effective WATI credentials for a send. {@code configId} is the tenant_provider_configs
     * row id when the creds came from the DB (used for {@code verified_at} stamping); null
     * when the JVM-global fallback was used.
     */
    public record WatiCreds(String baseUrl, String token, UUID configId) {}
}
