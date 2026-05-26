package in.schoolapp.communication.dispatcher;

import in.schoolapp.auth.IdentifierType;
import in.schoolapp.common.EmailNormalizer;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.notification.sms.SmsSender;
import in.schoolapp.tenantconfig.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single {@link in.schoolapp.communication.dispatcher.OtpDispatcher} implementation — routes
 * to {@link WhatsAppNotifier} for phone identifiers and {@link EmailSender} for email. Which
 * concrete notifier handles the send depends on the {@code app.whatsapp.provider} and
 * {@code app.email.provider} properties.
 * <p>
 * Plaintext OTP is logged at INFO level for dev convenience (the OTP won't show up in any
 * downstream WhatsApp/SMTP body we've chosen to render). Prod deployments should set
 * {@code logging.level.in.schoolapp.communication.dispatcher.DefaultOtpDispatcher=WARN} to
 * suppress.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOtpDispatcher implements OtpDispatcher {

    private static final String OTP_SUBJECT = "Your one-time verification code";

    private final WhatsAppNotifier whatsAppNotifier;
    private final EmailSender emailSender;
    private final SmsSender smsSender;

    /**
     * When the WhatsApp send throws and the active SMS provider isn't LOGGING, retry as SMS.
     * Lets schools whose parents don't have WhatsApp (or whose WATI quota is exhausted) still
     * receive OTPs. Toggleable via {@code app.otp.fallback.sms} — defaults to {@code true}
     * because the LOGGING SMS provider is a no-op anyway.
     */
    @Value("${app.otp.fallback.sms:true}")
    private boolean smsFallbackEnabled;

    @Override
    public void dispatch(String identifier, IdentifierType type, String otp) {
        String body = "Your verification code is " + otp + ". It is valid for 5 minutes.";

        switch (type) {
            case PHONE -> {
                log.info("[OTP-DISPATCH] channel=PHONE to={} otp={}",
                    PhoneNormalizer.mask(identifier), otp);
                try {
                    whatsAppNotifier.send(WhatsAppMessage.text(
                        identifier, body, WhatsAppMessage.MessageType.OTP));
                } catch (RuntimeException e) {
                    if (!smsFallbackEnabled
                        || smsSender == null
                        || ProviderType.LOGGING.equals(smsSender.providerName())) {
                        // No usable fallback — bubble up so the caller can return 502 / log alert.
                        throw e;
                    }
                    log.warn("WhatsApp OTP send failed ({}). Retrying via SMS provider={}",
                        e.getMessage(), smsSender.providerName());
                    // Tenant context is not yet wired into OTP send (identifier-only flow);
                    // pass null schoolId so the resolver falls back to env-level creds.
                    smsSender.send(null, identifier, body);
                }
            }
            case EMAIL -> {
                log.info("[OTP-DISPATCH] channel=EMAIL to={} otp={}",
                    EmailNormalizer.mask(identifier), otp);
                emailSender.send(identifier, OTP_SUBJECT, body);
            }
        }
    }
}
