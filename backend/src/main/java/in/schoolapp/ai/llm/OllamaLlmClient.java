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
 * Ollama — 100% free, self-hosted. Run {@code ollama serve} + {@code ollama pull llama3.1}
 * on the same host as the backend (or any host reachable on the {@code base_url}).
 *
 * <p>Endpoint: {@code POST {baseUrl}/api/chat} with body
 * {@code {"model": "...", "messages": [...], "stream": false}}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "OLLAMA")
@RequiredArgsConstructor
public class OllamaLlmClient implements LlmClient {

    private final LlmConfigResolver resolver;
    private final RestClient http = RestClient.create();

    @Override
    public String chat(UUID schoolId, String system, String userPrompt) {
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.OLLAMA.equals(r.provider()))
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "Ollama not configured for tenant " + schoolId));

        String url = (resolved.baseUrl() != null ? resolved.baseUrl() : "http://localhost:11434")
            + "/api/chat";

        Map<String, Object> body = Map.of(
            "model", resolved.model() != null ? resolved.model() : "llama3.1",
            "messages", List.of(
                Map.of("role", "system", "content", system != null ? system : ""),
                Map.of("role", "user", "content", userPrompt)
            ),
            "stream", false
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> message = resp != null ? (Map<String, Object>) resp.get("message") : null;
            String content = message != null ? String.valueOf(message.get("content")) : null;
            return content != null ? content.trim() : "";
        } catch (Exception e) {
            log.warn("Ollama chat failed: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Ollama call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return ProviderType.OLLAMA; }
}
