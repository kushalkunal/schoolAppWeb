package in.schoolapp.ocrservice.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.ocrservice.ocr.config.OcrProperties;
import in.schoolapp.ocrservice.ocr.dto.OcrResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Google Cloud Vision — DOCUMENT_TEXT_DETECTION preferred for handwriting-heavy school
 * artifacts (registers, mark sheets).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "GOOGLE_CLOUD_VISION")
public class GoogleCloudVisionOcrProvider implements OcrProvider {

    private static final String VISION_API_BASE = "https://vision.googleapis.com";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String featureType;

    public GoogleCloudVisionOcrProvider(RestClient.Builder builder, OcrProperties props,
                                        ObjectMapper objectMapper) {
        if (props.googleCloudVision() == null
            || props.googleCloudVision().apiKey() == null
            || props.googleCloudVision().apiKey().isBlank()) {
            throw new IllegalStateException(
                "app.ocr.provider=GOOGLE_CLOUD_VISION but app.ocr.google-cloud-vision.api-key is not set");
        }
        this.apiKey = props.googleCloudVision().apiKey();
        this.featureType = props.googleCloudVision().featureType() != null
            && !props.googleCloudVision().featureType().isBlank()
            ? props.googleCloudVision().featureType()
            : "DOCUMENT_TEXT_DETECTION";
        this.objectMapper = objectMapper;
        this.client = builder
            .baseUrl(VISION_API_BASE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public OcrResult extract(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("OCR image is empty");
        }
        try {
            Map<String, Object> body = Map.of("requests", List.of(Map.of(
                "image", Map.of("content", Base64.getEncoder().encodeToString(imageBytes)),
                "features", List.of(Map.of("type", featureType))
            )));
            String response = client.post()
                .uri("/v1/images:annotate?key={key}", apiKey)
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode firstResponse = root.path("responses").path(0);
            String text = firstResponse.path("fullTextAnnotation").path("text").asText("");
            log.debug("[OCR-VISION] extracted bytes={} chars={}", imageBytes.length, text.length());
            return OcrResult.of(text);
        } catch (Exception e) {
            log.error("Google Cloud Vision OCR failed: {}", e.getMessage());
            throw new RuntimeException("OCR provider failed: " + e.getMessage(), e);
        }
    }
}
