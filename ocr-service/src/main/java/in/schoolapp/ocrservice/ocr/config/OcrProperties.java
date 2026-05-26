package in.schoolapp.ocrservice.ocr.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
    @NotNull Provider provider,
    GoogleCloudVisionConfig googleCloudVision
) {
    public enum Provider { LOGGING, GOOGLE_CLOUD_VISION }

    public record GoogleCloudVisionConfig(String apiKey, String featureType) {}
}
