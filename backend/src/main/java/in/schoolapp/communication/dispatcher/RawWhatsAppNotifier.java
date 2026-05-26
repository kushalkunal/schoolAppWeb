package in.schoolapp.communication.dispatcher;

/**
 * Internal SPI for the concrete WhatsApp senders (LOGGING, WATI, Interakt, Meta Cloud). The
 * public-facing {@link WhatsAppNotifier} sits in front of this via
 * {@link in.schoolapp.communication.AuditingWhatsAppNotifier} so every call site automatically
 * gets a {@code notification_log} row without having to depend on the logger itself.
 * <p>
 * Return value is the BSP's own message id (e.g. Meta Cloud's {@code wamid.HBgN...}). Null
 * means the provider didn't return a correlation id — fine for the dev-mode logging sender.
 * Implementations <b>must throw</b> on hard failure so the decorator can mark the log row
 * FAILED.
 */
public interface RawWhatsAppNotifier {

    /**
     * @return the provider-assigned message id for webhook correlation, or {@code null} if
     *         the provider does not issue one.
     * @throws RuntimeException on dispatch failure (wrapped by the decorator into a log row).
     */
    String send(WhatsAppMessage message);
}
