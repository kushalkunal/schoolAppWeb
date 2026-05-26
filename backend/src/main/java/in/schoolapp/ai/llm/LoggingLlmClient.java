package in.schoolapp.ai.llm;

import in.schoolapp.tenantconfig.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default LLM — returns a canned reply and logs the prompt at DEBUG. Lets the chatbot
 * UI render end-to-end during dev / CI without burning quota.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingLlmClient implements LlmClient {

    @Override
    public String chat(UUID schoolId, String system, String userPrompt) {
        log.debug("[LLM-CHAT provider=LOGGING] school={} sysLen={} userLen={}",
            schoolId, system != null ? system.length() : 0, userPrompt != null ? userPrompt.length() : 0);
        return "I'm a placeholder reply. Set app.llm.provider to OLLAMA, GROQ, GEMINI or OPENROUTER "
            + "and provide credentials to get real answers.";
    }

    @Override
    public String providerName() { return ProviderType.LOGGING; }
}
