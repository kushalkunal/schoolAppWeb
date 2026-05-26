package in.schoolapp.auth.config;

/**
 * Governs which identifier(s) a new tenant may sign up with and subsequently use for OTP login.
 * <ul>
 *   <li>{@code PHONE} — phone only (default). OTP dispatched via WhatsApp/SMS.</li>
 *   <li>{@code EMAIL} — email only. OTP dispatched via email.</li>
 *   <li>{@code BOTH}  — signup must provide at least one; login works with whichever
 *       identifier the user supplies.</li>
 * </ul>
 * Controlled by {@code app.signup.channel} in application.yml.
 */
public enum SignupChannel {
    PHONE,
    EMAIL,
    BOTH;

    public boolean allowsPhone() { return this == PHONE || this == BOTH; }
    public boolean allowsEmail() { return this == EMAIL || this == BOTH; }
}
