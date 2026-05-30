package in.schoolapp.common;

/**
 * Role-aware PII masking for read responses. The VIEWER role is documented as "read-only, no PII
 * phone numbers" — this enforces that promise (audit fix #3) by masking contact details when the
 * current request's role is VIEWER. All other staff roles see full values.
 * <p>
 * Reads {@link TenantContext#getRole()} (the per-request role from the JWT), so DTO {@code from(...)}
 * factories can mask without threading the role through every call site. When the permission-based
 * RBAC model lands, swap the role check for an absence of a {@code *_READ_PII} permission.
 */
public final class PiiMasking {

    private static final String VIEWER = "VIEWER";

    private PiiMasking() {}

    /** True when the current caller must not see raw PII. */
    public static boolean restricted() {
        return VIEWER.equals(TenantContext.getRole());
    }

    /** Masks a 10-digit phone to {@code 98765****0} for VIEWER; returns it unchanged otherwise. */
    public static String phone(String phone) {
        return restricted() ? PhoneNormalizer.mask(phone) : phone;
    }

    /** Masks an email's local part to {@code a****@example.com} for VIEWER. */
    public static String email(String email) {
        if (!restricted() || email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return "****";
        }
        return email.charAt(0) + "****" + email.substring(at);
    }
}
