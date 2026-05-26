package in.schoolapp.notification.translation;

import in.schoolapp.tenantconfig.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * No-op translator — returns the source text unchanged. Active by default so feature flags
 * can stay off without breaking the dispatch chain. Logs the request so devs can see what
 * was about to be translated.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingTranslationService implements TranslationService {

    @Override
    public String translate(UUID schoolId, String text, String sourceLang, String targetLang) {
        log.debug("[TRANSLATE provider=LOGGING] school={} {}→{} textLen={}",
            schoolId, sourceLang, targetLang, text != null ? text.length() : 0);
        return text;
    }

    @Override
    public String providerName() { return ProviderType.LOGGING; }
}
