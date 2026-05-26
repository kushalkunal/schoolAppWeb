package in.schoolapp.communication.dispatcher.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * WhatsApp dispatch configuration. {@code provider=LOGGING} (default) keeps the dev-safe
 * console logger active; {@code provider=WATI} activates the real HTTP client — see
 * {@link in.schoolapp.communication.dispatcher.WatiWhatsAppNotifier}. When {@code provider=WATI}
 * all {@link WatiConfig} fields become effectively required (the HTTP client will throw
 * {@code IllegalArgumentException} on send if token/baseUrl are blank).
 */
@Validated
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
    @NotNull Provider provider,
    WatiConfig wati,
    /** HMAC-SHA256 secret used to verify inbound delivery-status webhooks. */
    String webhookSecret
) {
    public enum Provider { LOGGING, WATI }

    /**
     * @param baseUrl e.g. {@code https://live-mt-server.wati.io/<accountId>}
     * @param token   bearer token from WATI dashboard
     */
    public record WatiConfig(String baseUrl, String token) {}
}
