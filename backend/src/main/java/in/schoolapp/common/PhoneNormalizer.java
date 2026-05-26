package in.schoolapp.common;

import java.util.regex.Pattern;

/**
 * Canonicalises Indian phone numbers to a 10-digit form. All inbound phone numbers flow through
 * this before any DB lookup or WhatsApp dispatch — parents enter them inconsistently ("+91 98765
 * 43210", "098765-43210", "91 9876543210") and the system must unify them.
 */
public final class PhoneNormalizer {

    private static final Pattern NON_DIGIT = Pattern.compile("\\D");
    private static final Pattern VALID_10_DIGIT = Pattern.compile("^[6-9]\\d{9}$");

    private PhoneNormalizer() {}

    /**
     * Returns a 10-digit Indian mobile number. Strips non-digits, drops country code (91 or 0)
     * prefixes, and validates the mobile prefix (6-9). Throws {@link AppException} with
     * {@link ErrorCode#INVALID_PHONE} on failure.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.INVALID_PHONE, "Phone number is required");
        }
        String digits = NON_DIGIT.matcher(raw).replaceAll("");

        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        if (!VALID_10_DIGIT.matcher(digits).matches()) {
            throw new AppException(ErrorCode.INVALID_PHONE,
                "Phone must be a 10-digit Indian mobile number");
        }
        return digits;
    }

    /** Returns the +91-prefixed E.164 form used by WhatsApp BSPs. */
    public static String toE164(String normalizedTenDigit) {
        return "+91" + normalizedTenDigit;
    }

    /** Logging-safe mask: "9876543210" -> "98765****10". */
    public static String mask(String normalizedTenDigit) {
        if (normalizedTenDigit == null || normalizedTenDigit.length() != 10) {
            return "****";
        }
        return normalizedTenDigit.substring(0, 5) + "****" + normalizedTenDigit.substring(8);
    }
}
