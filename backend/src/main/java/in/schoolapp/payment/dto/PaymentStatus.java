package in.schoolapp.payment.dto;

/**
 * Provider-neutral payment outcome. Each real gateway (Stripe, Razorpay, PayU, …) maps its
 * own event vocabulary to one of these.
 */
public enum PaymentStatus {
    /** Payment captured successfully; safe to record as a {@link in.schoolapp.fee.entity.FeePayment}. */
    PAID,
    /** Terminal failure — card declined, link expired, UPI mandate failed, etc. */
    FAILED,
    /** Previously-paid transaction has been refunded in whole or part. */
    REFUNDED,
    /** Link created but no attempt made yet. */
    PENDING
}
