package in.schoolapp.notification.push;

import in.schoolapp.tenantconfig.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingPushNotifierTest {

    private final LoggingPushNotifier notifier = new LoggingPushNotifier();

    @Test
    void countsTokens() {
        int n = notifier.sendToTokens(UUID.randomUUID(),
            List.of("t1", "t2", "t3"),
            "Hello",
            "World",
            Map.of("deep_link", "/students/123"));
        assertThat(n).isEqualTo(3);
    }

    @Test
    void zeroForNullOrEmpty() {
        assertThat(notifier.sendToTokens(null, null, "t", "b", null)).isZero();
        assertThat(notifier.sendToTokens(null, List.of(), "t", "b", null)).isZero();
    }

    @Test
    void providerNameIsLogging() {
        assertThat(notifier.providerName()).isEqualTo(ProviderType.LOGGING);
    }
}
