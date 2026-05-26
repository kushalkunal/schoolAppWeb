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
 * OpenRouter — meta-gateway to 100+ models, OpenAI-compatible API. Many models tagged
 * {@code :free} (e.g. {@code meta-llama/llama-3.2-3b-instruct:free}) cost nothing.
 *
 * <p>Endpoint: {@code POST https://openrouter.ai/api/v1/chat/completions}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "OPENROUTER")
@RequiredArgsConstructor
public class OpenRouterLlmClient implements LlmClient {

    private static final String URL = "https://openrouter.ai/api/v1/chat/completions";

    private final LlmConfigResolver resolver;
    private final RestClient http = RestClient.create();

    @Override
    public String chat(UUID schoolId, String system, String userPrompt) {
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.OPENROUTER.equals(r.provider()))
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "OpenRouter not configured for tenant " + schoolId));

        Map<String, Object> body = Map.of(
            "model", resolved.model() != null ? resolved.model() : "meta-llama/llama-3.2-3b-instruct:free",
            "messages", List.of(
                Map.of("role", "system", "content", system != null ? system : ""),
                Map.of("role", "user", "content", userPrompt)
            )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolved.apiKey())
                // OpenRouter recommends these for attribution + rate-limit pooling.
                .header("HTTP-Referer", "https://schoolapp.in")
                .header("X-Title", "SchoolApp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            // OpenRouter response is OpenAI-shape → reuse the same extractor.
            return GroqLlmClient.extractContent(resp);
        } catch (Exception e) {
            log.warn("OpenRouter chat failed: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "OpenRouter call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return ProviderType.OPENROUTER; }
}
