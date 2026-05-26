package in.schoolapp.ai.llm;

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
 * Groq Cloud — free-tier OpenAI-compatible API, very fast LPU inference.
 * Sign up: {@code https://console.groq.com}. Free tier: ~14k requests/day on Llama 3.1.
 *
 * <p>Endpoint: {@code POST https://api.groq.com/openai/v1/chat/completions} (OpenAI shape).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "GROQ")
@RequiredArgsConstructor
public class GroqLlmClient implements LlmClient {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    private final LlmConfigResolver resolver;
    private final RestClient http = RestClient.create();

    @Override
    public String chat(UUID schoolId, String system, String userPrompt) {
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.GROQ.equals(r.provider()))
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "Groq not configured for tenant " + schoolId));

        Map<String, Object> body = Map.of(
            "model", resolved.model() != null ? resolved.model() : "llama-3.1-70b-versatile",
            "messages", List.of(
                Map.of("role", "system", "content", system != null ? system : ""),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.3
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolved.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            return extractContent(resp);
        } catch (Exception e) {
            log.warn("Groq chat failed: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Groq call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return ProviderType.GROQ; }

    static String extractContent(Map<String, Object> resp) {
        if (resp == null) return "";
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return "";
        Object content = message.get("content");
        return content != null ? content.toString().trim() : "";
    }
}
