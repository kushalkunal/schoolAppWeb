package in.schoolapp.communication.dispatcher;

import in.schoolapp.common.EmailNormalizer;
import in.schoolapp.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real SMTP sender. Activated by {@code app.email.provider=SMTP}.
 *
 * <p><strong>Slice 8c:</strong> SMTP credentials resolved per send via
 * {@link EmailConfigResolver} — tenant DB row first, JVM-global {@code spring.mail.*} env as
 * fallback. Per-tenant SMTP allows schools to send from their own branded domain
 * (e.g. {@code noreply@sunshinepublic.in}) while the platform's default relay stays available
 * for OTP dispatches that don't have a tenant context yet.
 *
 * <p>Per-(host, port, username) {@link JavaMailSenderImpl} instances cached so a steady
 * configuration reuses the same client across sends.
 *
 * <p>Tenant context: read from {@link TenantContext} when present. OTP dispatches that happen
 * before login (no JWT, no TenantContext) fall through to the env fallback automatically.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "SMTP")
public class SmtpEmailSender implements EmailSender {

    private final EmailConfigResolver configResolver;
    private final ConcurrentHashMap<String, JavaMailSenderImpl> senderCache = new ConcurrentHashMap<>();

    public SmtpEmailSender(EmailConfigResolver configResolver) {
        this.configResolver = configResolver;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        UUID schoolId = TenantContext.getTenantId();   // null for pre-auth (OTP) flows
        Optional<EmailConfigResolver.SmtpCreds> credsOpt = configResolver.resolve(schoolId);
        if (credsOpt.isEmpty()) {
            log.warn("[SMTP-SKIP] no SMTP creds available for school={} to={} — message dropped",
                schoolId, EmailNormalizer.mask(toEmail));
            return;
        }
        EmailConfigResolver.SmtpCreds creds = credsOpt.get();
        JavaMailSenderImpl sender = senderFor(creds);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(creds.from());
            msg.setTo(toEmail);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            log.debug("[SMTP-SENT] school={} to={} subject=\"{}\"",
                schoolId, EmailNormalizer.mask(toEmail), subject);
        } catch (Exception e) {
            log.error("SMTP send failed to={} — {}", EmailNormalizer.mask(toEmail), e.getMessage());
        }
    }

    private JavaMailSenderImpl senderFor(EmailConfigResolver.SmtpCreds c) {
        String cacheKey = c.host() + ":" + c.port() + "@" + c.username();
        return senderCache.computeIfAbsent(cacheKey, k -> {
            JavaMailSenderImpl s = new JavaMailSenderImpl();
            s.setHost(c.host());
            s.setPort(c.port());
            s.setUsername(c.username());
            s.setPassword(c.password());
            Properties p = s.getJavaMailProperties();
            p.put("mail.transport.protocol", "smtp");
            p.put("mail.smtp.auth", Boolean.toString(c.auth()));
            p.put("mail.smtp.starttls.enable", Boolean.toString(c.startTls()));
            return s;
        });
    }
}
