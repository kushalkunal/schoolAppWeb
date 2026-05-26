package in.schoolapp.communication.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatiWhatsAppNotifierTest {

    @Mock WhatsAppConfigResolver resolver;
    @Mock RestClient.Builder restBuilder;

    @Test
    void send_returnsNull_andSkipsHttp_whenNoCredsResolved() {
        UUID schoolId = UUID.randomUUID();
        when(resolver.resolve(schoolId)).thenReturn(Optional.empty());

        WatiWhatsAppNotifier notifier = new WatiWhatsAppNotifier(
            restBuilder, new ObjectMapper(), resolver);

        WhatsAppMessage msg = WhatsAppMessage.text("9876543210", "hi", MessageType.ABSENCE_ALERT,
            WhatsAppMessage.Audit.forSchool(schoolId));

        String result = notifier.send(msg);

        assertThat(result).isNull();
        // No HTTP client built because no creds resolved.
        verifyNoInteractions(restBuilder);
        verify(resolver).resolve(schoolId);
    }

    @Test
    void send_consultsResolver_usingSchoolIdFromAuditContext() {
        UUID schoolId = UUID.randomUUID();
        when(resolver.resolve(schoolId)).thenReturn(Optional.empty());

        WatiWhatsAppNotifier notifier = new WatiWhatsAppNotifier(
            restBuilder, new ObjectMapper(), resolver);

        notifier.send(WhatsAppMessage.text("9876543210", "x", MessageType.FEE_REMINDER,
            WhatsAppMessage.Audit.forSchool(schoolId)));

        verify(resolver).resolve(schoolId);
    }

    @Test
    void send_consultsResolver_withNullSchoolId_whenNoAudit() {
        // OTP-style dispatch: no audit context → resolver called with null (env fallback only).
        when(resolver.resolve(null)).thenReturn(Optional.empty());

        WatiWhatsAppNotifier notifier = new WatiWhatsAppNotifier(
            restBuilder, new ObjectMapper(), resolver);

        notifier.send(WhatsAppMessage.text("9876543210", "otp 123", MessageType.OTP));

        verify(resolver).resolve(null);
    }

    @Test
    void send_doesNotValidateCredsAtConstruction() {
        // Constructor was previously strict — verify we can build the bean even with a
        // resolver that always returns empty. Boot-time relaxation lets a JVM serve schools
        // that have per-tenant configs without any JVM-global env vars.
        WatiWhatsAppNotifier notifier = new WatiWhatsAppNotifier(
            restBuilder, new ObjectMapper(), resolver);
        when(resolver.resolve(any())).thenReturn(Optional.empty());

        assertThat(notifier).isNotNull();
        assertThat(notifier.send(
            WhatsAppMessage.text("9876543210", "x", MessageType.OTP)
        )).isNull();
    }
}
