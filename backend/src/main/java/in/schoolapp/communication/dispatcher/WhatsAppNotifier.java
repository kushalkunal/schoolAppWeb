package in.schoolapp.communication.dispatcher;

/**
 * Outbound WhatsApp abstraction. Consumers (AbsenceAlertService, ReceiptDeliveryListener,
 * FeeReminderService) depend on this interface; the WATI/Interakt HTTP client drops in behind
 * it in Slice 5 without any consumer code changes.
 */
public interface WhatsAppNotifier {

    /** Dispatches a message. Non-blocking from the caller's perspective. */
    void send(WhatsAppMessage message);
}
