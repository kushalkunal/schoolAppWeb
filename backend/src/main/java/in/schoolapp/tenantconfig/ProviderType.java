package in.schoolapp.tenantconfig;

import java.util.Set;

/**
 * Stable provider-name constants. Plain strings (not an enum) so a new provider can be added
 * by inserting a row + adding one constant, without touching switch-statements.
 *
 * <p>The {@link #allowedFor(ProviderConcern)} method is what the platform-admin API consults
 * to validate that {@code concern=PAYMENT, provider=GEMINI} is rejected at write time.
 */
public final class ProviderType {

    private ProviderType() {}

    // Universal — every concern supports a no-op logging provider for dev / disabled state.
    public static final String LOGGING = "LOGGING";

    // WhatsApp
    public static final String WATI       = "WATI";
    public static final String TWILIO_WA  = "TWILIO_WA";
    public static final String INTERAKT   = "INTERAKT";

    // Email
    public static final String SMTP       = "SMTP";
    public static final String SES        = "SES";
    public static final String MAILGUN    = "MAILGUN";
    public static final String SENDGRID   = "SENDGRID";

    // Payment
    public static final String STRIPE     = "STRIPE";
    public static final String RAZORPAY   = "RAZORPAY";

    // Storage
    public static final String LOCAL      = "LOCAL";
    public static final String S3         = "S3";       // works against R2 / B2 / MinIO / AWS

    // OCR
    public static final String GOOGLE_CLOUD_VISION = "GOOGLE_CLOUD_VISION";

    // LLM — OPENAI / ANTHROPIC / GEMINI are paid tiers (Gemini has a free tier too).
    // OLLAMA / GROQ / OPENROUTER are the free-tier options exposed by Slice 19.
    public static final String OPENAI     = "OPENAI";
    public static final String ANTHROPIC  = "ANTHROPIC";
    public static final String GEMINI     = "GEMINI";
    public static final String OLLAMA     = "OLLAMA";       // self-hosted, 100% free
    public static final String GROQ       = "GROQ";         // free tier, fast
    public static final String OPENROUTER = "OPENROUTER";   // free + paid tiers via one key

    // SMS (Slice 14)
    public static final String TWILIO     = "TWILIO";
    public static final String MSG91      = "MSG91";
    public static final String AWS_SNS    = "AWS_SNS";

    // Push (Slice 14)
    public static final String FCM        = "FCM";
    public static final String APNS       = "APNS";

    // Translation (Slice 14)
    public static final String GOOGLE_TRANSLATE = "GOOGLE_TRANSLATE";
    public static final String DEEPL      = "DEEPL";

    /** Closed set of provider strings valid for a given concern. */
    public static Set<String> allowedFor(ProviderConcern concern) {
        return switch (concern) {
            case WHATSAPP    -> Set.of(LOGGING, WATI, TWILIO_WA, INTERAKT);
            case EMAIL       -> Set.of(LOGGING, SMTP, SES, MAILGUN, SENDGRID);
            case PAYMENT     -> Set.of(LOGGING, STRIPE, RAZORPAY);
            case STORAGE     -> Set.of(LOCAL, S3);
            case OCR         -> Set.of(LOGGING, GOOGLE_CLOUD_VISION);
            case LLM         -> Set.of(LOGGING, OPENAI, ANTHROPIC, GEMINI, OLLAMA, GROQ, OPENROUTER);
            case SMS         -> Set.of(LOGGING, TWILIO, MSG91, AWS_SNS);
            case PUSH        -> Set.of(LOGGING, FCM, APNS);
            case TRANSLATION -> Set.of(LOGGING, GOOGLE_TRANSLATE, DEEPL, OPENAI);
        };
    }
}
