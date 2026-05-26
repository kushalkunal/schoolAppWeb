package in.schoolapp.payment;

import in.schoolapp.common.AppException;
import in.schoolapp.payment.config.PaymentProperties;
import in.schoolapp.payment.config.PaymentProperties.Provider;
import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import in.schoolapp.payment.dto.PaymentLinkRequest.PayerInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLinkServiceTest {

    @Mock PaymentGateway gateway;

    @Test
    void createLink_passesRequiredFieldsToGateway() {
        PaymentProperties props = new PaymentProperties(Provider.LOGGING, "INR",
            "https://app.schoolapp.in/payment/success", null, null);
        PaymentLinkService service = new PaymentLinkService(gateway, props);

        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(gateway.createPaymentLink(any())).thenReturn(new PaymentLink(
            "dev-link-x", "https://pay.example/x", 450000L, "INR", OffsetDateTime.now()));

        service.createLink(tenantId, studentId, 450000L, "Term 1 fees",
            new PayerInfo("Ramesh", "9876543210", "r@example.com"));

        ArgumentCaptor<PaymentLinkRequest> captor = ArgumentCaptor.forClass(PaymentLinkRequest.class);
        verify(gateway).createPaymentLink(captor.capture());
        PaymentLinkRequest sent = captor.getValue();

        assertThat(sent.tenantId()).isEqualTo(tenantId);
        assertThat(sent.studentId()).isEqualTo(studentId);
        assertThat(sent.amountPaise()).isEqualTo(450000L);
        assertThat(sent.currency()).isEqualTo("INR");
        assertThat(sent.purpose()).isEqualTo("Term 1 fees");
        assertThat(sent.payer().name()).isEqualTo("Ramesh");
        assertThat(sent.returnUrl()).isEqualTo("https://app.schoolapp.in/payment/success");
        assertThat(sent.validFor()).isNotNull();
    }

    @Test
    void createLink_rejectsNonPositiveAmount() {
        PaymentProperties props = new PaymentProperties(Provider.LOGGING, "INR", null, null, null);
        PaymentLinkService service = new PaymentLinkService(gateway, props);

        assertThatThrownBy(() -> service.createLink(
            UUID.randomUUID(), UUID.randomUUID(), 0L, "x", null
        )).isInstanceOf(AppException.class);

        assertThatThrownBy(() -> service.createLink(
            UUID.randomUUID(), UUID.randomUUID(), -100L, "x", null
        )).isInstanceOf(AppException.class);
    }
}
