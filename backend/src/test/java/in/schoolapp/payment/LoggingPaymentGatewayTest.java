package in.schoolapp.payment;

import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import in.schoolapp.payment.dto.PaymentLinkRequest.PayerInfo;
import in.schoolapp.payment.gateway.LoggingPaymentGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingPaymentGatewayTest {

    private final LoggingPaymentGateway gateway = new LoggingPaymentGateway();

    @Test
    void createPaymentLink_returnsDeterministicShape() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        PaymentLink link = gateway.createPaymentLink(new PaymentLinkRequest(
            tenantId, studentId, 450000L, "INR", "Term 1 fees",
            new PayerInfo("Ramesh", "9876543210", null),
            null, Duration.ofDays(7)
        ));

        assertThat(link.providerReference()).startsWith("dev-link-");
        assertThat(link.url())
            .contains(tenantId.toString())
            .contains(studentId.toString())
            .contains("amount=450000");
        assertThat(link.amountPaise()).isEqualTo(450000L);
        assertThat(link.currency()).isEqualTo("INR");
        assertThat(link.expiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void createPaymentLink_defaultsCurrencyToInr() {
        PaymentLink link = gateway.createPaymentLink(new PaymentLinkRequest(
            UUID.randomUUID(), UUID.randomUUID(), 100L, null, null, null, null, null
        ));
        assertThat(link.currency()).isEqualTo("INR");
    }
}
