package in.schoolapp.ai.llm;

import java.util.UUID;

/**
 * General-purpose LLM chat abstraction. Used by the parent / staff chatbot
 * (Slice 19), the auto-grader (future), and any other AI-flavoured feature.
 *
 * <p>Implementation contract:
 * <ul>
 *   <li>The default LOGGING impl returns a canned reply — useful for dev / CI without
 *       hitting any external service.</li>
 *   <li>Real providers (OLLAMA / GROQ / GEMINI / OPENAI / ANTHROPIC / OPENROUTER) are
 *       all selected via {@code app.llm.provider} at startup. Per-tenant override flows
 *       through {@link LlmConfigResolver} the same way WhatsApp / SMS / Translation do.</li>
 *   <li>Implementations MUST be safe to call with {@code schoolId=null} (system prompts)
 *       and a missing tenant config — falling back to JVM-global env creds.</li>
 * </ul>
 *
 * <p>Cost note for school admins:
 * <ul>
 *   <li><strong>OLLAMA</strong> — runs locally / on your own VM, $0 forever, slow.</li>
 *   <li><strong>GROQ</strong> — free tier ~14K req/day with Llama 3.1, very fast.</li>
 *   <li><strong>GEMINI</strong> — free tier ~1.5K req/day with Gemini Flash.</li>
 *   <li><strong>OPENROUTER</strong> — some models marked {@code :free} have no charge.</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * Single-turn chat. {@code system} primes behaviour (role / tone / language);
     * {@code userPrompt} is the actual question.
     *
     * @return the model's reply, trimmed of leading/trailing whitespace.
     */
    String chat(UUID schoolId, String system, String userPrompt);

    /** Provider key — matches {@link in.schoolapp.tenantconfig.ProviderType}. */
    String providerName();
}
