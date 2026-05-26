package in.schoolapp.notification.sms;

import in.schoolapp.tenantconfig.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingSmsSenderTest {

    private final LoggingSmsSender sender = new LoggingSmsSender();

    @Test
    void returnsLoggedSentinel() {
        String id = sender.send(UUID.randomUUID(), "9876543210", "Your code is 123456");
        assertThat(id).isEqualTo("logged");
    }

    @Test
    void providerNameIsLogging() {
        assertThat(sender.providerName()).isEqualTo(ProviderType.LOGGING);
    }

    @Test
    void handlesNullSchool() {
        // System-level OTP sends pass null schoolId; sender must tolerate it.
        assertThat(sender.send(null, "9876543210", "body")).isEqualTo("logged");
    }
}
