package in.schoolapp.notification.sms.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JVM-global SMS configuration. Per-tenant overrides via TenantProviderConfig take precedence;
 * these env values are the deployment-wide fallback (handy for an org running one SMS account
 * across many schools).
 *
 * <p>Every field is optional — when blank, the resolver returns empty and the sender no-ops
 * (or fails depending on caller).
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
    /**
     * Active provider key. One of LOGGING (default), TWILIO, MSG91, AWS_SNS.
     * Pattern is permissive — provider list lives in {@link in.schoolapp.tenantconfig.ProviderType}.
     */
    @Pattern(regexp = "LOGGING|TWILIO|MSG91|AWS_SNS|") String provider,
    /** Twilio credentials — used when provider=TWILIO. */
    Twilio twilio,
    /** MSG91 credentials — used when provider=MSG91. */
    Msg91 msg91
) {
    public record Twilio(String accountSid, String authToken, String fromNumber) {}
    public record Msg91(String authKey, String senderId, String templateId, String routeId) {}
}
