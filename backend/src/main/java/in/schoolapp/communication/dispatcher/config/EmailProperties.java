package in.schoolapp.communication.dispatcher.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email dispatch configuration. {@code provider=LOGGING} (default) logs to stdout;
 * {@code provider=SMTP} delegates to Spring's {@link org.springframework.mail.javamail.JavaMailSender}
 * — the user must additionally configure {@code spring.mail.host}, {@code username},
 * {@code password}, and STARTTLS properties.
 */
@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
    @NotNull Provider provider,
    @NotBlank String from
) {
    public enum Provider { LOGGING, SMTP }
}
