package in.schoolapp.communication.dispatcher;

import in.schoolapp.common.EmailNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev default — logs would-be emails to stdout with a masked address. Active unless
 * {@code app.email.provider=SMTP} wires in the real sender.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final int BODY_PREVIEW_MAX = 240;

    @Override
    public void send(String toEmail, String subject, String body) {
        String preview = body == null ? ""
            : (body.length() > BODY_PREVIEW_MAX ? body.substring(0, BODY_PREVIEW_MAX) + "…" : body)
                .replace('\n', ' ');
        log.info("[EMAIL-SEND] to={} subject=\"{}\" body=\"{}\"",
            EmailNormalizer.mask(toEmail), subject, preview);
    }
}
