package in.schoolapp.ai.llm;

import in.schoolapp.ai.llm.config.LlmProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves effective LLM creds at chat time. Tenant config → env → empty.
 *
 * <p>Returned record exposes {@code baseUrl} (for OLLAMA / OPENROUTER which can be
 * self-hosted), {@code apiKey} (null for OLLAMA), and {@code model}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final LlmProperties globalProps;

    public Optional<ResolvedLlm> resolve(UUID schoolId) {
        // 1. Per-tenant config row wins.
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.LLM);
            if (perTenant.isPresent()) {
                String provider = perTenant.get().provider();
                if (ProviderType.LOGGING.equals(provider)) return Optional.empty();
                Object apiKey = perTenant.get().config().get("api_key");
                Object baseUrl = perTenant.get().config().get("base_url");
                Object model = perTenant.get().config().get("model");
                String keyStr = apiKey instanceof String s && !s.isBlank() ? s : null;
                String urlStr = baseUrl instanceof String s && !s.isBlank() ? s : null;
                String modelStr = model instanceof String s && !s.isBlank() ? s : null;
                // Ollama is happy without an api_key.
                if (ProviderType.OLLAMA.equals(provider) || keyStr != null) {
                    return Optional.of(new ResolvedLlm(provider, urlStr, keyStr, modelStr, perTenant.get().id()));
                }
                log.warn("Tenant {} LLM row missing api_key", schoolId);
            }
        }

        // 2. JVM-global env fallback.
        if (globalProps == null || globalProps.provider() == null) return Optional.empty();
        String p = globalProps.provider();
        return switch (p) {
            case "OLLAMA" -> Optional.of(new ResolvedLlm(p,
                globalProps.ollama() != null ? globalProps.ollama().baseUrlOrDefault() : "http://localhost:11434",
                null,
                globalProps.ollama() != null ? globalProps.ollama().modelOrDefault() : "llama3.1",
                null));
            case "GROQ" -> globalProps.groq() != null && nonBlank(globalProps.groq().apiKey())
                ? Optional.of(new ResolvedLlm(p, null, globalProps.groq().apiKey(),
                    globalProps.groq().modelOrDefault(), null))
                : Optional.empty();
            case "GEMINI" -> globalProps.gemini() != null && nonBlank(globalProps.gemini().apiKey())
                ? Optional.of(new ResolvedLlm(p, null, globalProps.gemini().apiKey(),
                    globalProps.gemini().modelOrDefault(), null))
                : Optional.empty();
            case "OPENAI" -> globalProps.openai() != null && nonBlank(globalProps.openai().apiKey())
                ? Optional.of(new ResolvedLlm(p, null, globalProps.openai().apiKey(),
                    globalProps.openai().modelOrDefault(), null))
                : Optional.empty();
            case "ANTHROPIC" -> globalProps.anthropic() != null && nonBlank(globalProps.anthropic().apiKey())
                ? Optional.of(new ResolvedLlm(p, null, globalProps.anthropic().apiKey(),
                    globalProps.anthropic().modelOrDefault(), null))
                : Optional.empty();
            case "OPENROUTER" -> globalProps.openrouter() != null && nonBlank(globalProps.openrouter().apiKey())
                ? Optional.of(new ResolvedLlm(p, null, globalProps.openrouter().apiKey(),
                    globalProps.openrouter().modelOrDefault(), null))
                : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static boolean nonBlank(String s) { return s != null && !s.isBlank(); }

    public record ResolvedLlm(String provider, String baseUrl, String apiKey, String model, UUID configId) {}
}
