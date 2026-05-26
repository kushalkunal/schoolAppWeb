package in.schoolapp.ocrservice.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.ocrservice.JobType;
import in.schoolapp.ocrservice.dto.ExtractedRecord;
import in.schoolapp.ocrservice.llm.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ANTHROPIC")
public class AnthropicLlmProvider implements LlmExtractionProvider {

    private static final String ANTHROPIC_BASE = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";
    private static final int MAX_TOKENS = 4096;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    public AnthropicLlmProvider(RestClient.Builder builder, LlmProperties props, ObjectMapper objectMapper) {
        if (props.anthropic() == null
            || props.anthropic().apiKey() == null || props.anthropic().apiKey().isBlank()) {
            throw new IllegalStateException(
                "app.llm.provider=ANTHROPIC but app.llm.anthropic.api-key is not set");
        }
        this.objectMapper = objectMapper;
        this.model = props.anthropic().model() != null && !props.anthropic().model().isBlank()
            ? props.anthropic().model() : DEFAULT_MODEL;
        this.client = builder
            .baseUrl(ANTHROPIC_BASE)
            .defaultHeader("x-api-key", props.anthropic().apiKey())
            .defaultHeader("anthropic-version", API_VERSION)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public List<ExtractedRecord> extract(String ocrText, JobType type) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", PromptTemplates.SYSTEM,
                "messages", List.of(Map.of(
                    "role", "user", "content", PromptTemplates.userPromptFor(type, ocrText)
                )),
                "temperature", 0
            );
            String response = client.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("content").path(0).path("text").asText("");
            log.debug("[LLM-ANTHROPIC] type={} model={} responseChars={}", type, model, content.length());
            return LlmJsonParser.parse(content, objectMapper);
        } catch (Exception e) {
            log.error("Anthropic extraction failed type={}: {}", type, e.getMessage());
            throw new RuntimeException("Anthropic LLM call failed: " + e.getMessage(), e);
        }
    }
}
