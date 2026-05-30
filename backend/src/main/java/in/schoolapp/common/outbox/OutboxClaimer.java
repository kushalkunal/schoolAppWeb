package in.schoolapp.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Atomically claims a batch of due outbox events for one poller, making the drain loop safe to run
 * on multiple instances (audit #5). Separate from {@link OutboxPoller} on purpose: the claim must
 * run through Spring's transactional proxy, and a self-invoked {@code @Transactional} method would
 * silently run with no transaction.
 * <p>
 * The claim holds {@code FOR UPDATE SKIP LOCKED} row locks for the duration of this transaction and
 * pushes each row's {@code next_attempt_at} a visibility window into the future, so that after the
 * locks release a concurrent poller's "due" filter still skips them. If this instance then crashes
 * before processing, the rows simply resurface once the window elapses (at-least-once).
 */
@Component
@RequiredArgsConstructor
public class OutboxClaimer {

    private final OutboxEventRepository repository;

    @Transactional
    public List<OutboxEvent> claim(int batch, Duration visibility) {
        List<OutboxEvent> due = repository.findDueForUpdate(OffsetDateTime.now(), batch);
        if (due.isEmpty()) {
            return due;
        }
        OffsetDateTime invisibleUntil = OffsetDateTime.now().plus(visibility);
        for (OutboxEvent row : due) {
            row.setNextAttemptAt(invisibleUntil);   // hide from other pollers while we process
        }
        repository.saveAll(due);
        return due;
    }
}
