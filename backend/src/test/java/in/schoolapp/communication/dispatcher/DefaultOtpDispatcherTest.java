package in.schoolapp.communication.dispatcher;

import in.schoolapp.auth.IdentifierType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DefaultOtpDispatcherTest {

    @Mock WhatsAppNotifier whatsAppNotifier;
    @Mock EmailSender emailSender;

    @InjectMocks DefaultOtpDispatcher dispatcher;

    @Test
    void phone_routesToWhatsAppNotifier_onlyOnce() {
        dispatcher.dispatch("9876543210", IdentifierType.PHONE, "123456");

        ArgumentCaptor<WhatsAppMessage> captor = ArgumentCaptor.forClass(WhatsAppMessage.class);
        verify(whatsAppNotifier).send(captor.capture());
        verifyNoInteractions(emailSender);

        WhatsAppMessage sent = captor.getValue();
        assertThat(sent.toPhone()).isEqualTo("9876543210");
        assertThat(sent.type()).isEqualTo(WhatsAppMessage.MessageType.OTP);
        assertThat(sent.body()).contains("123456");
    }

    @Test
    void email_routesToEmailSender_onlyOnce() {
        dispatcher.dispatch("principal@school.in", IdentifierType.EMAIL, "987654");

        verify(emailSender).send(
            org.mockito.ArgumentMatchers.eq("principal@school.in"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.contains("987654")
        );
        verifyNoInteractions(whatsAppNotifier);
    }
}
