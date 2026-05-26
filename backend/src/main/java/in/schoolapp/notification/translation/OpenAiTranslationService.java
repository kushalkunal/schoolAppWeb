package in.schoolapp.notification.translation;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.tenantconfig.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM-backed translation via OpenAI chat completions. Higher cost than Google Translate,
 * but better for nuanced text (e.g. parent-friendly tone in a teacher's remark).
 *
 * <p>Active when {@code app.translation.provider=OPENAI}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "OPENAI")
@RequiredArgsConstructor
public class OpenAiTranslationService implements TranslationService {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final TranslationConfigResolver resolver;
    private final RestClient http = RestClient.create();

    @Override
    public String translate(UUID schoolId, String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return text;
        if (targetLang == null || targetLang.isBlank()) return text;

        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.OPENAI.equals(r.provider()))
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "No OpenAI API key configured for tenant " + schoolId));

        String prompt = "Translate the following text into " + langName(targetLang)
            + ". Output only the translation, no explanations. Preserve formatting and "
            + "newlines exactly.\n\nText:\n" + text;

        Map<String, Object> body = Map.of(
            "model", resolved.modelOrTier() != null ? resolved.modelOrTier() : "gpt-4o-mini",
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolved.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) (resp != null ? resp.get("choices") : null);
            if (choices == null || choices.isEmpty()) return text;
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message != null ? String.valueOf(message.get("content")) : null;
            return content != null && !content.isBlank() ? content.trim() : text;
        } catch (Exception e) {
            log.warn("OpenAI translate failed school={} reason={}", schoolId, e.getMessage());
            return text;
        }
    }

    @Override
    public String providerName() { return ProviderType.OPENAI; }

    private static String langName(String code) {
        return switch (code) {
            case "en" -> "English";
            case "hi" -> "Hindi";
            case "ta" -> "Tamil";
            case "te" -> "Telugu";
            case "mr" -> "Marathi";
            case "bn" -> "Bengali";
            case "gu" -> "Gujarati";
            case "kn" -> "Kannada";
            case "ml" -> "Malayalam";
            case "pa" -> "Punjabi";
            case "ur" -> "Urdu";
            default   -> code;
        };
    }
}
