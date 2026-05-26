package in.schoolapp.payment.dto;

import java.time.Duration;
import java.util.UUID;

/**
 * Provider-neutral input for creating a hosted payment URL. Gateways translate the fields to
 * their own API shape (Stripe Checkout Session, Razorpay Payment Link, …).
 * <p>
 * {@code amountPaise} is always in the smallest currency unit (paise for INR, cents for USD)
 * consistent with the rest of the codebase. {@code metadata} rides through the provider's
 * notes/metadata map so webhooks can correlate a payment event back to a tenant + student.
 */
public record PaymentLinkRequest(
    UUID tenantId,
    UUID studentId,
    long amountPaise,
    String currency,           // e.g. "INR", "USD"
    String purpose,            // short description, shown to payer
    PayerInfo payer,
    String returnUrl,          // where to redirect after payment (Stripe); optional for Razorpay
    Duration validFor
) {
    public record PayerInfo(String name, String phone, String email) {}
}
