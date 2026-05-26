package in.schoolapp.communication.dispatcher;

/**
 * Outbound email abstraction — mirror of {@link WhatsAppNotifier} for the email channel.
 * Consumers (primarily {@link in.schoolapp.communication.dispatcher.DefaultOtpDispatcher})
 * code against this interface so a real SMTP client drops in via configuration without any
 * caller-side change.
 */
public interface EmailSender {

    void send(String toEmail, String subject, String body);
}
