package in.schoolapp.ai.llm;

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
 * Google Gemini API — free tier ~1.5k requests/day with gemini-1.5-flash.
 * Get key at {@code https://aistudio.google.com/app/apikey}.
 *
 * <p>Endpoint:
 * {@code POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}}
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "GEMINI")
@RequiredArgsConstructor
public class GeminiLlmClient implements LlmClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final LlmConfigResolver resolver;
    private final RestClient http = RestClient.create();

    @Override
    public String chat(UUID schoolId, String system, String userPrompt) {
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.GEMINI.equals(r.provider()))
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "Gemini not configured for tenant " + schoolId));

        String model = resolved.model() != null ? resolved.model() : "gemini-1.5-flash";
        String url = BASE + model + ":generateContent?key=" + resolved.apiKey();

        // Gemini's "system" is a separate top-level field, not in messages.
        Map<String, Object> body = new java.util.HashMap<>();
        if (system != null && !system.isBlank()) {
            body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", system))
            ));
        }
        body.put("contents", List.of(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", userPrompt))
        )));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) (resp != null ? resp.get("candidates") : null);
            if (candidates == null || candidates.isEmpty()) return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) return "";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) return "";
            Object text = parts.get(0).get("text");
            return text != null ? text.toString().trim() : "";
        } catch (Exception e) {
            log.warn("Gemini chat failed: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Gemini call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return ProviderType.GEMINI; }
}
