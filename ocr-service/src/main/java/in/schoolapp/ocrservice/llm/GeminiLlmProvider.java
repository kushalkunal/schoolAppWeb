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
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "GEMINI")
public class GeminiLlmProvider implements LlmExtractionProvider {

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-1.5-flash";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    public GeminiLlmProvider(RestClient.Builder builder, LlmProperties props, ObjectMapper objectMapper) {
        if (props.gemini() == null
            || props.gemini().apiKey() == null || props.gemini().apiKey().isBlank()) {
            throw new IllegalStateException(
                "app.llm.provider=GEMINI but app.llm.gemini.api-key is not set");
        }
        this.objectMapper = objectMapper;
        this.apiKey = props.gemini().apiKey();
        this.model = props.gemini().model() != null && !props.gemini().model().isBlank()
            ? props.gemini().model() : DEFAULT_MODEL;
        this.client = builder
            .baseUrl(GEMINI_BASE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public List<ExtractedRecord> extract(String ocrText, JobType type) {
        try {
            Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", PromptTemplates.SYSTEM))),
                "contents", List.of(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", PromptTemplates.userPromptFor(type, ocrText)))
                )),
                "generationConfig", Map.of(
                    "responseMimeType", "application/json",
                    "temperature", 0
                )
            );
            String response = client.post()
                .uri("/v1/models/{model}:generateContent?key={key}", model, apiKey)
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text").asText("");
            log.debug("[LLM-GEMINI] type={} model={} responseChars={}", type, model, content.length());
            return LlmJsonParser.parse(content, objectMapper);
        } catch (Exception e) {
            log.error("Gemini extraction failed type={}: {}", type, e.getMessage());
            throw new RuntimeException("Gemini LLM call failed: " + e.getMessage(), e);
        }
    }
}
