package in.schoolapp.ocrservice.llm.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @NotNull Provider provider,
    OpenAiConfig openai,
    AnthropicConfig anthropic,
    GeminiConfig gemini
) {
    public enum Provider { LOGGING, OPENAI, ANTHROPIC, GEMINI }
    public record OpenAiConfig(String apiKey, String model) {}
    public record AnthropicConfig(String apiKey, String model) {}
    public record GeminiConfig(String apiKey, String model) {}
}
