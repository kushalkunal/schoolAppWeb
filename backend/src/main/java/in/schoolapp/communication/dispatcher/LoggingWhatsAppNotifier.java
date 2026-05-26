package in.schoolapp.communication.dispatcher;

import in.schoolapp.common.PhoneNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-safe default. Active when {@code app.whatsapp.provider} is unset or set to
 * {@code LOGGING}; superseded by {@link WatiWhatsAppNotifier} when {@code provider=WATI}.
 * <p>
 * The log format deliberately includes the full body (truncated) so manual smoke-testing
 * without WATI can still verify template rendering. No provider-assigned message id exists in
 * dev, so {@link #send(WhatsAppMessage)} returns {@code null} — webhook correlation is skipped
 * but the {@code notification_log} row is still created with status SENT.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingWhatsAppNotifier implements RawWhatsAppNotifier {

    private static final int BODY_PREVIEW_MAX = 240;

    @Override
    public String send(WhatsAppMessage message) {
        String preview = message.body() == null ? ""
            : (message.body().length() > BODY_PREVIEW_MAX
                ? message.body().substring(0, BODY_PREVIEW_MAX) + "…"
                : message.body())
                .replace('\n', ' ');
        String masked = PhoneNormalizer.mask(message.toPhone());
        String media = message.mediaUrl() == null ? "" : " media=" + message.mediaUrl();
        log.info("[WA-SEND type={}] to={}{} body=\"{}\"", message.type(), masked, media, preview);
        return null;
    }
}
