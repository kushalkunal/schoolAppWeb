package in.schoolapp.payment.dto;

import java.time.OffsetDateTime;

/**
 * Created-link handle returned by a {@link in.schoolapp.payment.PaymentGateway}. The
 * {@code providerReference} lets downstream code correlate webhook events back to this link
 * (see {@link PaymentEvent#providerReference()}).
 */
public record PaymentLink(
    String providerReference,
    String url,
    long amountPaise,
    String currency,
    OffsetDateTime expiresAt
) {}
