package in.schoolapp.notification.translation;

import java.util.UUID;

/**
 * Text translation abstraction. Used by circulars + inbox replies + report-card remarks
 * to deliver content in the parent's preferred language.
 *
 * <p>Default behaviour ({@code LOGGING}) is a no-op that returns the source unchanged.
 * Real providers (GOOGLE_TRANSLATE, DEEPL, OPENAI) plug in via {@code @ConditionalOnProperty}.
 */
public interface TranslationService {

    /**
     * @param schoolId   tenant whose creds we resolve (per-tenant config first)
     * @param text       source text
     * @param sourceLang ISO-639-1 code (e.g. "en"); null = auto-detect
     * @param targetLang ISO-639-1 code (e.g. "hi" for Hindi)
     * @return translated text, or the original if translation is muted / unavailable
     */
    String translate(UUID schoolId, String text, String sourceLang, String targetLang);

    /** Provider key (matches {@link in.schoolapp.tenantconfig.ProviderType}). */
    String providerName();
}
