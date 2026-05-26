package in.schoolapp.notification.push;

import in.schoolapp.tenantconfig.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default push notifier — logs the would-be send. Active when {@code app.push.provider}
 * is unset or set to {@code LOGGING}. Lets dev / CI exercise the dispatch chain without
 * needing real FCM credentials.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingPushNotifier implements PushNotifier {

    @Override
    public int sendToTokens(UUID schoolId, List<String> tokens, String title, String body, Map<String, String> data) {
        int n = tokens == null ? 0 : tokens.size();
        log.info("[PUSH-SEND provider=LOGGING] school={} tokens={} title=\"{}\" dataKeys={}",
            schoolId, n, title, data != null ? data.keySet() : "[]");
        return n;
    }

    @Override
    public String providerName() { return ProviderType.LOGGING; }
}
