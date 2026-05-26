package in.schoolapp.notification.sms;

import in.schoolapp.notification.sms.config.SmsProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Effective SMS provider + creds for an outbound send.
 *
 * <p>Resolution order — mirrors {@code WhatsAppConfigResolver}:
 * <ol>
 *   <li>Per-tenant config row for {@code (schoolId, SMS)}.</li>
 *   <li>JVM-global {@link SmsProperties} (env-driven).</li>
 *   <li>Empty → caller falls back to LOGGING.</li>
 * </ol>
 *
 * <p>Provider {@code LOGGING} at the tenant level means "muted" — the caller skips the send
 * silently. This is intentional and distinct from "unconfigured".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final SmsProperties globalProps;

    public Optional<ResolvedSms> resolve(UUID schoolId) {
        // 1. Per-tenant config.
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.SMS);
            if (perTenant.isPresent()) {
                String provider = perTenant.get().provider();
                if (ProviderType.LOGGING.equals(provider)) return Optional.empty();
                if (ProviderType.TWILIO.equals(provider)) {
                    Object sid   = perTenant.get().config().get("account_sid");
                    Object tok   = perTenant.get().config().get("auth_token");
                    Object from  = perTenant.get().config().get("from_number");
                    if (allStrings(sid, tok, from)) {
                        return Optional.of(new ResolvedSms(
                            ProviderType.TWILIO,
                            new TwilioCreds((String) sid, (String) tok, (String) from),
                            null,
                            perTenant.get().id()));
                    }
                    log.warn("Tenant {} has TWILIO SMS row but account_sid / auth_token / from_number missing",
                        schoolId);
                }
                if (ProviderType.MSG91.equals(provider)) {
                    Object key  = perTenant.get().config().get("auth_key");
                    Object send = perTenant.get().config().get("sender_id");
                    Object tpl  = perTenant.get().config().get("template_id");
                    Object route = perTenant.get().config().get("route_id");
                    if (allStrings(key, send)) {
                        return Optional.of(new ResolvedSms(
                            ProviderType.MSG91,
                            null,
                            new Msg91Creds((String) key, (String) send,
                                tpl instanceof String s ? s : null,
                                route instanceof String r ? r : null),
                            perTenant.get().id()));
                    }
                    log.warn("Tenant {} has MSG91 SMS row but auth_key / sender_id missing", schoolId);
                }
            }
        }

        // 2. JVM-global env fallback.
        if (globalProps != null) {
            String provider = globalProps.provider();
            if ("TWILIO".equals(provider) && globalProps.twilio() != null
                && isNonBlank(globalProps.twilio().accountSid())
                && isNonBlank(globalProps.twilio().authToken())
                && isNonBlank(globalProps.twilio().fromNumber())) {
                return Optional.of(new ResolvedSms(
                    ProviderType.TWILIO,
                    new TwilioCreds(globalProps.twilio().accountSid(),
                        globalProps.twilio().authToken(),
                        globalProps.twilio().fromNumber()),
                    null,
                    null));
            }
            if ("MSG91".equals(provider) && globalProps.msg91() != null
                && isNonBlank(globalProps.msg91().authKey())
                && isNonBlank(globalProps.msg91().senderId())) {
                return Optional.of(new ResolvedSms(
                    ProviderType.MSG91,
                    null,
                    new Msg91Creds(globalProps.msg91().authKey(),
                        globalProps.msg91().senderId(),
                        globalProps.msg91().templateId(),
                        globalProps.msg91().routeId()),
                    null));
            }
        }

        return Optional.empty();
    }

    private static boolean allStrings(Object... values) {
        for (Object v : values) {
            if (!(v instanceof String s) || s.isBlank()) return false;
        }
        return true;
    }

    private static boolean isNonBlank(String s) { return s != null && !s.isBlank(); }

    /** Discriminated union of provider creds. Exactly one of {@code twilio}/{@code msg91} is non-null. */
    public record ResolvedSms(String provider, TwilioCreds twilio, Msg91Creds msg91, UUID configId) {}
    public record TwilioCreds(String accountSid, String authToken, String fromNumber) {}
    public record Msg91Creds(String authKey, String senderId, String templateId, String routeId) {}
}
