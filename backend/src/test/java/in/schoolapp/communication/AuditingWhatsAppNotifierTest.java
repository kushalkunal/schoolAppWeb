package in.schoolapp.communication;

import in.schoolapp.communication.dispatcher.RawWhatsAppNotifier;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditingWhatsAppNotifierTest {

    @Mock RawWhatsAppNotifier raw;
    @Mock NotificationLogger logger;

    @InjectMocks AuditingWhatsAppNotifier notifier;

    @Test
    void sendMarksSent_whenRawReturnsMessageId() {
        UUID logId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        WhatsAppMessage msg = WhatsAppMessage.text("9876543210", "hello", MessageType.FEE_RECEIPT,
            WhatsAppMessage.Audit.forParent(schoolId, studentId, parentId, "Ramesh"));
        when(logger.recordQueued(msg)).thenReturn(Optional.of(logId));
        when(raw.send(msg)).thenReturn("wamid.ABC123");

        notifier.send(msg);

        verify(logger).markSent(eq(logId), eq("wamid.ABC123"));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void sendMarksFailed_whenRawThrows() {
        UUID logId = UUID.randomUUID();
        WhatsAppMessage msg = WhatsAppMessage.text("9876543210", "hello", MessageType.ABSENCE_ALERT,
            WhatsAppMessage.Audit.forSchool(UUID.randomUUID()));
        when(logger.recordQueued(msg)).thenReturn(Optional.of(logId));
        when(raw.send(msg)).thenThrow(new RuntimeException("BSP 502"));

        notifier.send(msg);

        verify(logger).markFailed(eq(logId), contains("BSP 502"));
    }

    @Test
    void sendSkipsLogger_whenNoAuditContext() {
        // OTP and other call-sites that don't know schoolId → no log row written.
        WhatsAppMessage msg = WhatsAppMessage.text("9876543210", "hello", MessageType.OTP);
        when(logger.recordQueued(msg)).thenReturn(Optional.empty());
        when(raw.send(msg)).thenReturn(null);

        notifier.send(msg);

        verify(raw).send(msg);
        verify(logger).recordQueued(any());
        verifyNoMoreInteractions(logger);
    }
}
