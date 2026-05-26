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
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "OPENAI")
public class OpenAiLlmProvider implements LlmExtractionProvider {

    private static final String OPENAI_BASE = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiLlmProvider(RestClient.Builder builder, LlmProperties props, ObjectMapper objectMapper) {
        if (props.openai() == null
            || props.openai().apiKey() == null || props.openai().apiKey().isBlank()) {
            throw new IllegalStateException(
                "app.llm.provider=OPENAI but app.llm.openai.api-key is not set");
        }
        this.objectMapper = objectMapper;
        this.model = props.openai().model() != null && !props.openai().model().isBlank()
            ? props.openai().model() : DEFAULT_MODEL;
        this.client = builder
            .baseUrl(OPENAI_BASE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.openai().apiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public List<ExtractedRecord> extract(String ocrText, JobType type) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", PromptTemplates.SYSTEM),
                    Map.of("role", "user",   "content", PromptTemplates.userPromptFor(type, ocrText))
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0
            );
            String response = client.post()
                .uri("/v1/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            log.debug("[LLM-OPENAI] type={} model={} responseChars={}", type, model, content.length());
            return LlmJsonParser.parse(content, objectMapper);
        } catch (Exception e) {
            log.error("OpenAI extraction failed type={}: {}", type, e.getMessage());
            throw new RuntimeException("OpenAI LLM call failed: " + e.getMessage(), e);
        }
    }
}
