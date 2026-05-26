package in.schoolapp.notification.translation;

import in.schoolapp.tenantconfig.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingTranslationServiceTest {

    private final LoggingTranslationService svc = new LoggingTranslationService();

    @Test
    void returnsSourceUnchanged() {
        String src = "Class will start at 8 am";
        assertThat(svc.translate(UUID.randomUUID(), src, "en", "hi")).isEqualTo(src);
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(svc.translate(null, null, "en", "hi")).isNull();
        assertThat(svc.translate(null, "", "en", "hi")).isEmpty();
    }

    @Test
    void providerNameIsLogging() {
        assertThat(svc.providerName()).isEqualTo(ProviderType.LOGGING);
    }
}
