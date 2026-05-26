package in.schoolapp.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes events into the {@code outbox_events} table — the row is committed atomically
 * with the caller's business write. The {@link OutboxPoller} (a separate transaction) reads
 * pending rows and republishes them through Spring's {@code ApplicationEventPublisher} so
 * the existing async listeners pick them up unchanged.
 *
 * <p>Existing direct event publication (e.g. {@code eventPublisher.publishEvent(...)}) is
 * still valid for events that don't need restart durability — slice 9c adds the outbox
 * <em>option</em>, not a replacement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Persist an event for durable delivery. Call this <em>inside</em> the business
     * transaction. If the transaction rolls back, the outbox row goes with it — no
     * orphan events.
     *
     * @param schoolId tenant binding (null for platform-level events)
     * @param event    the event object; must be Jackson-serialisable
     */
    @Transactional
    public UUID publish(UUID schoolId, Object event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(event, Map.class);

            OutboxEvent row = new OutboxEvent();
            row.setSchoolId(schoolId);
            row.setTopic(event.getClass().getName());
            row.setPayload(payload);
            row.setAttempts(0);
            row.setNextAttemptAt(OffsetDateTime.now());
            row = repository.save(row);
            log.debug("Outbox published topic={} id={} school={}", row.getTopic(), row.getId(), schoolId);
            return row.getId();
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Failed to write outbox event: " + e.getMessage(), e);
        }
    }
}
