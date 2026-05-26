package in.schoolapp.tenantconfig;

/**
 * The external-integration concerns a tenant can configure independently. Persisted as VARCHAR
 * in {@code tenant_provider_configs.concern} — never rename.
 */
public enum ProviderConcern {
    /** Outbound parent / staff WhatsApp messaging. */
    WHATSAPP,
    /** SMTP / SES / Mailgun / SendGrid / OTP email. */
    EMAIL,
    /** Online fee payment provider — Stripe / Razorpay. */
    PAYMENT,
    /** File storage backend — local / S3-compatible (R2 / B2 / AWS / MinIO). */
    STORAGE,
    /** OCR engine for paper-register migration. */
    OCR,
    /** LLM for structured extraction in the migration pipeline. */
    LLM,
    /** SMS provider (Twilio, MSG91, AWS SNS, …) — fallback for WhatsApp + transactional alerts. */
    SMS,
    /** Mobile push provider (FCM / APNs) — wakes the parent + teacher native apps. */
    PUSH,
    /** Translation service (Google, DeepL, OpenAI) — auto-translate circulars per parent language. */
    TRANSLATION
}
