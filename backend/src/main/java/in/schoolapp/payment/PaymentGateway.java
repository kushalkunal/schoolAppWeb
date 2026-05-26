package in.schoolapp.payment;

import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;

/**
 * Payment-provider abstraction. Callers ({@link in.schoolapp.fee.FeeReminderService} and any
 * future canteen/library-fine flows) depend on this interface; the concrete provider
 * (Stripe, Razorpay, or the LOGGING stub) is selected via {@code app.payment.provider}.
 * <p>
 * Webhook verification is handled by a separate provider-specific controller
 * (e.g. {@code StripeWebhookController}, {@code RazorpayWebhookController}) which is
 * responsible for translating the provider payload into a provider-neutral
 * {@link in.schoolapp.payment.dto.PaymentEvent}.
 */
public interface PaymentGateway {

    /**
     * Issues a hosted payment URL for the given request.
     *
     * @throws in.schoolapp.common.AppException with {@link in.schoolapp.common.ErrorCode#EXTERNAL_SERVICE_ERROR}
     *         if the provider API call fails. Callers running in {@code @Async} paths must catch and log.
     */
    PaymentLink createPaymentLink(PaymentLinkRequest request);
}
