package in.schoolapp.ai.llm.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JVM-global LLM configuration. Per-tenant credentials override via
 * {@code tenant_provider_configs} on concern {@code LLM}.
 *
 * <p>Free-tier defaults to make this provider trivially usable:
 * <ul>
 *   <li>Set {@code app.llm.provider=OLLAMA} + run {@code ollama serve} locally → free.</li>
 *   <li>Set {@code app.llm.provider=GROQ} + sign up at console.groq.com for a free key → free.</li>
 *   <li>Set {@code app.llm.provider=GEMINI} + key from aistudio.google.com → free tier.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @Pattern(regexp = "LOGGING|OLLAMA|GROQ|GEMINI|OPENAI|ANTHROPIC|OPENROUTER|")
    String provider,
    Ollama ollama,
    Groq groq,
    Gemini gemini,
    Openai openai,
    Anthropic anthropic,
    Openrouter openrouter
) {
    /**
     * Ollama runs locally; only the URL + model name needed. No API key.
     * Default URL works against the standard local install ({@code ollama serve}).
     */
    public record Ollama(String baseUrl, String model) {
        public String baseUrlOrDefault() { return baseUrl != null && !baseUrl.isBlank() ? baseUrl : "http://localhost:11434"; }
        public String modelOrDefault()   { return model != null && !model.isBlank() ? model : "llama3.1"; }
    }

    public record Groq(String apiKey, String model) {
        public String modelOrDefault() { return model != null && !model.isBlank() ? model : "llama-3.1-70b-versatile"; }
    }

    public record Gemini(String apiKey, String model) {
        public String modelOrDefault() { return model != null && !model.isBlank() ? model : "gemini-1.5-flash"; }
    }

    public record Openai(String apiKey, String model) {
        public String modelOrDefault() { return model != null && !model.isBlank() ? model : "gpt-4o-mini"; }
    }

    public record Anthropic(String apiKey, String model) {
        public String modelOrDefault() { return model != null && !model.isBlank() ? model : "claude-3-5-haiku-latest"; }
    }

    public record Openrouter(String apiKey, String model) {
        public String modelOrDefault() { return model != null && !model.isBlank() ? model : "meta-llama/llama-3.2-3b-instruct:free"; }
    }
}
