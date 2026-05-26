package in.schoolapp.notification.translation;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.tenantconfig.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Google Cloud Translation v2 (the simple {@code GET} flavour with an API key).
 *
 * <p>v3 supports IAM-scoped service accounts, but for a per-school API-key flow v2 is
 * the natural fit. Schools that want stricter access controls plug in a service account
 * through a different provider class.
 *
 * <p>Endpoint:
 * {@code POST https://translation.googleapis.com/language/translate/v2?key={apiKey}}
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "GOOGLE_TRANSLATE")
@RequiredArgsConstructor
public class GoogleTranslationService implements TranslationService {

    private static final String URL = "https://translation.googleapis.com/language/translate/v2";

    private final TranslationConfigResolver resolver;
    private final RestClient http = RestClient.create();

    @Override
    public String translate(UUID schoolId, String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return text;
        if (targetLang == null || targetLang.isBlank()) return text;

        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.GOOGLE_TRANSLATE.equals(r.provider()))
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "No Google Translate API key configured for tenant " + schoolId));

        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("q", text);
            body.put("target", targetLang);
            if (sourceLang != null && !sourceLang.isBlank()) body.put("source", sourceLang);
            body.put("format", "text");

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post()
                .uri(URL + "?key=" + resolved.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) (resp != null ? resp.get("data") : null);
            if (data == null) return text;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> translations = (List<Map<String, Object>>) data.get("translations");
            if (translations == null || translations.isEmpty()) return text;

            String translated = String.valueOf(translations.get(0).get("translatedText"));
            log.debug("[TRANSLATE provider=GOOGLE_TRANSLATE] school={} {}→{} len {}→{}",
                schoolId, sourceLang, targetLang, text.length(), translated.length());
            return translated;
        } catch (Exception e) {
            log.warn("Google translate failed school={} reason={}", schoolId, e.getMessage());
            // Degrade gracefully — return source so the message still goes out.
            return text;
        }
    }

    @Override
    public String providerName() { return ProviderType.GOOGLE_TRANSLATE; }
}
