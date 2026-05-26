package in.schoolapp.notification.translation.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.translation")
public record TranslationProperties(
    @Pattern(regexp = "LOGGING|GOOGLE_TRANSLATE|DEEPL|OPENAI|") String provider,
    Google google,
    Deepl deepl,
    Openai openai
) {
    public record Google(String apiKey) {}
    public record Deepl(String apiKey, boolean freeTier) {}
    public record Openai(String apiKey, String model) {}
}
