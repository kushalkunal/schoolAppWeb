package in.schoolapp.notification.sms;

import java.util.UUID;

/**
 * Outbound SMS abstraction. Three concrete implementations ship by default
 * (LOGGING / TWILIO / MSG91); add new ones by writing one class + one
 * {@code @ConditionalOnProperty(name = "app.sms.provider", havingValue = "...")}.
 *
 * <p>Like {@code WhatsAppNotifier}, the active implementation is selected by the
 * {@code app.sms.provider} property at startup, but per-tenant credentials flow
 * through {@link SmsConfigResolver} at send time — so a tenant can run on
 * Twilio while the platform default is MSG91.
 *
 * <p>Never log {@code body} at INFO level — it may contain OTPs.
 */
public interface SmsSender {

    /**
     * Send the message and return the provider's message ID (for tracing / webhooks).
     * Throws an {@link in.schoolapp.common.AppException} on hard failure so the caller
     * can decide whether to fall back to email / WhatsApp.
     *
     * @param schoolId  the tenant whose creds we resolve (null for system-level sends)
     * @param toPhone   E.164-ish phone (with or without leading +; the impl normalises)
     * @param body      the message body; impls apply provider-specific length truncation
     * @return provider-issued message id, or {@code "logged"} for the LOGGING impl
     */
    String send(UUID schoolId, String toPhone, String body);

    /** Provider key — matches {@link in.schoolapp.tenantconfig.ProviderType}. */
    String providerName();
}
