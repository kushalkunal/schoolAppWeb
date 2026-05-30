package in.schoolapp.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reads pending {@link OutboxEvent} rows every 5 seconds and republishes them via Spring's
 * {@link ApplicationEventPublisher}. Existing {@code @EventListener} / {@code @Async}
 * listeners receive the event identically to how they would for an in-process publish —
 * the outbox is invisible to them.
 *
 * <p>Retry strategy: on dispatch failure, increment {@code attempts} and push
 * {@code next_attempt_at} forward with exponential backoff capped at 10 minutes. After
 * 10 attempts the row stays in the table for manual review — no automatic poisoning.
 *
 * <p>Multi-instance safe (audit #5): the batch is claimed via {@link OutboxClaimer} using
 * {@code FOR UPDATE SKIP LOCKED} plus a visibility window, so concurrent pollers on other JVMs
 * claim disjoint rows instead of double-publishing the same event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    /** Batch size per poll — bounded so a backed-up table doesn't pin the poller thread. */
    private static final int BATCH = 50;
    /** Cap on backoff between retries. */
    private static final int MAX_BACKOFF_SECONDS = 600;
    /** After this many failed attempts the row stays in the table for manual review. */
    private static final int GIVE_UP_AFTER = 10;
    /** How long a claimed-but-not-yet-processed row stays hidden from other pollers. */
    private static final Duration CLAIM_VISIBILITY = Duration.ofSeconds(60);

    private final OutboxClaimer claimer;
    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5_000)
    public void drain() {
        List<OutboxEvent> due = claimer.claim(BATCH, CLAIM_VISIBILITY);
        if (due.isEmpty()) return;

        int processed = 0;
        int failed = 0;
        for (OutboxEvent row : due) {
            // Each row in its own transaction so one failure doesn't roll back the others.
            try {
                processOne(row);
                processed++;
            } catch (Exception e) {
                failed++;
                markFailure(row, e);
            }
        }
        log.debug("Outbox drain processed={} failed={} (batch={})", processed, failed, due.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void processOne(OutboxEvent row) throws Exception {
        Class<?> eventClass = Class.forName(row.getTopic());
        Object event = objectMapper.convertValue(row.getPayload(), eventClass);
        events.publishEvent(event);
        row.setProcessedAt(OffsetDateTime.now());
        repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailure(OutboxEvent row, Exception e) {
        int attempts = row.getAttempts() + 1;
        row.setAttempts(attempts);
        row.setLastError(truncate(e.toString()));
        if (attempts >= GIVE_UP_AFTER) {
            // Don't keep retrying forever; row remains for ops to inspect.
            log.error("Outbox event id={} topic={} failed {} times — giving up: {}",
                row.getId(), row.getTopic(), attempts, e.toString());
            row.setNextAttemptAt(OffsetDateTime.now().plus(1, ChronoUnit.DAYS));
        } else {
            long backoffSeconds = Math.min((long) Math.pow(2, attempts), MAX_BACKOFF_SECONDS);
            row.setNextAttemptAt(OffsetDateTime.now().plusSeconds(backoffSeconds));
            log.warn("Outbox event id={} attempt {}/{} failed: {} (next try in {}s)",
                row.getId(), attempts, GIVE_UP_AFTER, e.toString(), backoffSeconds);
        }
        repository.save(row);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) + "…" : s;
    }
}
