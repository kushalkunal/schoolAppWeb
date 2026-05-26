package in.schoolapp.communication.dispatcher;

import in.schoolapp.auth.IdentifierType;

/**
 * Delivers a one-time password via the appropriate channel (WhatsApp/SMS for phone, SMTP for
 * email). Slice 4 ships with a logging-only implementation so the end-to-end flow works on a
 * developer machine without external credentials; Slice 5 swaps in the real WATI and
 * JavaMailSender implementations behind this interface.
 */
public interface OtpDispatcher {

    /**
     * @param identifier normalised phone (10 digits) or email (lowercased)
     * @param type       which channel the identifier is — drives template selection
     * @param otp        plaintext 6-digit code
     */
    void dispatch(String identifier, IdentifierType type, String otp);
}
