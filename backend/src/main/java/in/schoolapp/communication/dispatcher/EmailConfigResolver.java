package in.schoolapp.communication.dispatcher;

import in.schoolapp.communication.dispatcher.config.EmailProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves effective SMTP credentials for a tenant. Mirrors {@code WhatsAppConfigResolver}.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code tenant_provider_configs(schoolId, EMAIL, SMTP)} with non-blank host + username.</li>
 *   <li>JVM-global {@code spring.mail.*} props (used as fallback for OTP pre-auth dispatches
 *       and for tenants without a per-school config row).</li>
 *   <li>Empty — caller drops to {@code LoggingEmailSender} behaviour.</li>
 * </ol>
 *
 * <p>Tenant explicitly choosing {@code LOGGING} returns empty (intentional mute, distinct from
 * "not configured").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final EmailProperties globalProps;

    // Spring's spring.mail.* properties are available indirectly via Boot autoconfig; we read
    // them directly so the resolver doesn't depend on JavaMailSenderImpl assembly order.
    @Value("${spring.mail.host:}")              private String envHost;
    @Value("${spring.mail.port:587}")           private int envPort;
    @Value("${spring.mail.username:}")          private String envUsername;
    @Value("${spring.mail.password:}")          private String envPassword;
    @Value("${spring.mail.properties.mail.smtp.auth:true}")       private boolean envAuth;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") private boolean envStartTls;

    public Optional<SmtpCreds> resolve(UUID schoolId) {
        // 1. Per-tenant row (if any).
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.EMAIL);
            if (perTenant.isPresent()) {
                String provider = perTenant.get().provider();
                if (ProviderType.LOGGING.equals(provider)) {
                    return Optional.empty();   // tenant explicitly muted email
                }
                if (ProviderType.SMTP.equals(provider)) {
                    Object host = perTenant.get().config().get("host");
                    Object port = perTenant.get().config().get("port");
                    Object user = perTenant.get().config().get("username");
                    Object pass = perTenant.get().config().get("password");
                    Object from = perTenant.get().config().get("from");
                    if (host instanceof String h && !h.isBlank()
                        && user instanceof String u && !u.isBlank()) {
                        return Optional.of(new SmtpCreds(
                            h,
                            port instanceof Number n ? n.intValue() : envPort,
                            u,
                            pass instanceof String p ? p : "",
                            from instanceof String f ? f : (globalProps.from() == null ? u : globalProps.from()),
                            envAuth,
                            envStartTls,
                            perTenant.get().id()
                        ));
                    }
                    log.warn("Tenant {} has SMTP config row but host/username missing", schoolId);
                }
                // SES / MAILGUN / SENDGRID would land here — wire when needed.
            }
        }
        // 2. JVM-global fallback.
        if (envHost != null && !envHost.isBlank()) {
            return Optional.of(new SmtpCreds(
                envHost, envPort, envUsername, envPassword,
                globalProps.from() == null ? envUsername : globalProps.from(),
                envAuth, envStartTls, null
            ));
        }
        return Optional.empty();
    }

    /**
     * Resolved SMTP config. {@code configId} is the {@code tenant_provider_configs} row id
     * when creds came from the DB; null when JVM env was used.
     */
    public record SmtpCreds(
        String host, int port, String username, String password, String from,
        boolean auth, boolean startTls, UUID configId
    ) {}
}
