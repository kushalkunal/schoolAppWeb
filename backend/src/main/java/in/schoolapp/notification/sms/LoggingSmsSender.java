package in.schoolapp.notification.sms;

import in.schoolapp.tenantconfig.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default SMS sender — logs to the backend console instead of sending. Active when
 * {@code app.sms.provider} is unset or set to {@code LOGGING}. Useful in dev / CI and as
 * the safe-by-default behaviour for a school that hasn't paid for an SMS account.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    @Override
    public String send(UUID schoolId, String toPhone, String body) {
        // Mask phone to last 4 digits in case body contains nothing identifying.
        String masked = mask(toPhone);
        log.info("[SMS-SEND provider=LOGGING] school={} to={} bodyLen={}", schoolId, masked, body.length());
        return "logged";
    }

    @Override
    public String providerName() { return ProviderType.LOGGING; }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }
}
