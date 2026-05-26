package in.schoolapp.common;

import java.util.regex.Pattern;

/**
 * Canonicalises email addresses for use as login identifiers. All emails flow through this
 * before DB lookup so "User@Example.com", "user@example.com", and "  user@example.com "
 * resolve to the same staff record.
 */
public final class EmailNormalizer {

    // Intentionally permissive — catches typos, not RFC edge cases. Real validation happens
    // when we send the first email and get a bounce.
    private static final Pattern BASIC_EMAIL = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private EmailNormalizer() {}

    /** Lowercases and trims; rejects anything that doesn't look like an email. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Email is required");
        }
        String trimmed = raw.trim().toLowerCase();
        if (!BASIC_EMAIL.matcher(trimmed).matches()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Email address is not valid");
        }
        return trimmed;
    }

    /** Logging-safe mask: "rajesh@example.com" -> "ra****@example.com". */
    public static String mask(String normalized) {
        if (normalized == null) return "****";
        int at = normalized.indexOf('@');
        if (at <= 2) return "****" + (at >= 0 ? normalized.substring(at) : "");
        return normalized.substring(0, 2) + "****" + normalized.substring(at);
    }
}
