package in.schoolapp.ocrservice;

import in.schoolapp.ocrservice.llm.config.LlmProperties;
import in.schoolapp.ocrservice.ocr.config.OcrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * OCR + LLM extraction microservice. Stateless — accepts (image, jobType) and returns
 * structured records. Main backend's MigrationProcessor calls this over HTTP.
 *
 * <p>Provider selection (LOGGING vs GOOGLE_CLOUD_VISION / OPENAI / ANTHROPIC / GEMINI) is via
 * env vars; per-tenant overrides are a future addition once we wire the SchoolApp backend's
 * {@code tenant_provider_configs} reader as a sidecar lookup.
 */
@SpringBootApplication
@EnableConfigurationProperties({OcrProperties.class, LlmProperties.class})
public class OcrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OcrServiceApplication.class, args);
    }
}
