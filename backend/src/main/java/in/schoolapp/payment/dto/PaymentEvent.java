package in.schoolapp.payment.dto;

import java.util.Map;

/**
 * Provider-neutral webhook event. Each gateway's webhook handler is responsible for parsing
 * the provider-specific payload into this shape. Downstream listeners (e.g. an auto-record
 * listener that creates a {@link in.schoolapp.fee.entity.FeePayment}) subscribe to this
 * instead of the raw provider event — so the listener code doesn't change when a tenant
 * switches providers.
 */
public record PaymentEvent(
    String providerReference,
    PaymentStatus status,
    long amountPaise,
    String paymentMethod,       // "upi", "card", "netbanking", … (provider-reported)
    Map<String, Object> metadata  // contains tenant_id, student_id, …
) {}
