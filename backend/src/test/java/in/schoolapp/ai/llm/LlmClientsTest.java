package in.schoolapp.ai.llm;

import in.schoolapp.tenantconfig.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientsTest {

    @Test
    void loggingClientReturnsPlaceholder() {
        LoggingLlmClient c = new LoggingLlmClient();
        String reply = c.chat(UUID.randomUUID(), "be helpful", "what's 2+2?");
        assertThat(reply).isNotBlank().contains("placeholder");
        assertThat(c.providerName()).isEqualTo(ProviderType.LOGGING);
    }

    @Test
    void loggingClientHandlesNulls() {
        LoggingLlmClient c = new LoggingLlmClient();
        assertThat(c.chat(null, null, "hi")).isNotBlank();
    }

    @Test
    void groqResponseExtractorParsesOpenAiShape() {
        Map<String, Object> resp = Map.of(
            "choices", List.of(Map.of(
                "message", Map.of(
                    "role", "assistant",
                    "content", "  Hello, world!  "
                )
            ))
        );
        assertThat(GroqLlmClient.extractContent(resp)).isEqualTo("Hello, world!");
    }

    @Test
    void groqResponseExtractorHandlesEmpty() {
        assertThat(GroqLlmClient.extractContent(null)).isEmpty();
        assertThat(GroqLlmClient.extractContent(Map.of())).isEmpty();
        assertThat(GroqLlmClient.extractContent(Map.of("choices", List.of()))).isEmpty();
    }
}
