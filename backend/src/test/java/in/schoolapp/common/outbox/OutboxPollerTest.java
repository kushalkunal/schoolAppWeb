package in.schoolapp.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock OutboxEventRepository repository;
    @Mock ApplicationEventPublisher events;

    OutboxPoller poller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        poller = new OutboxPoller(repository, events, om);
    }

    @Test
    void drain_emptyBatch_isANoOp() {
        when(repository.findDue(any(OffsetDateTime.class), any(Pageable.class))).thenReturn(List.of());
        poller.drain();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void processOne_publishesEvent_andMarksProcessedAt() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "hello");
        OutboxEvent row = newRow(SampleEvent.class.getName(), payload);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        poller.processOne(row);

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue()).isInstanceOf(SampleEvent.class);
        assertThat(((SampleEvent) ev.getValue()).text()).isEqualTo("hello");
        assertThat(row.getProcessedAt()).isNotNull();
    }

    @Test
    void markFailure_incrementsAttempts_andSchedulesBackoff() {
        OutboxEvent row = newRow("does.not.exist.Event", Map.of("x", 1));
        row.setAttempts(2);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        poller.markFailure(row, new RuntimeException("broken"));

        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getLastError()).contains("broken");
        assertThat(row.getNextAttemptAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void markFailure_givesUp_after10Attempts() {
        OutboxEvent row = newRow("does.not.exist.Event", Map.of("x", 1));
        row.setAttempts(9);                       // one more failure pushes it past the cap
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        poller.markFailure(row, new RuntimeException("still broken"));

        assertThat(row.getAttempts()).isEqualTo(10);
        // Next attempt pushed ~1 day out so it stops bothering the poller.
        assertThat(row.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusHours(20));
    }

    @Test
    void drain_oneFailure_doesNotStopOthers() throws Exception {
        // Two rows: first deserialises fine, second has a bad topic. processOne is invoked
        // per-row in its own transaction, so failure on one doesn't stop the other.
        OutboxEvent good = newRow(SampleEvent.class.getName(), Map.of("text", "ok"));
        OutboxEvent bad = newRow("nope.NotARealClass", Map.of("x", 1));
        when(repository.findDue(any(OffsetDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(good, bad));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        poller.drain();
        // Good was published; bad was marked-failed.
        verify(events).publishEvent(any(SampleEvent.class));
        assertThat(good.getProcessedAt()).isNotNull();
        assertThat(bad.getAttempts()).isEqualTo(1);
    }

    // --- helpers ---

    private static OutboxEvent newRow(String topic, Map<String, Object> payload) {
        OutboxEvent row = new OutboxEvent();
        row.setId(UUID.randomUUID());
        row.setTopic(topic);
        row.setPayload(payload);
        row.setNextAttemptAt(OffsetDateTime.now());
        return row;
    }

    /** Simple Jackson-deserialisable record used as a test event payload. */
    public record SampleEvent(String text) {}
}
