package in.schoolapp.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Pull a batch of pending events whose {@code next_attempt_at} has come due. Limit via
     * the {@link Pageable} argument so a single poll doesn't try to drain a backed-up table.
     */
    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.processedAt IS NULL
          AND o.nextAttemptAt <= :now
        ORDER BY o.nextAttemptAt ASC
        """)
    List<OutboxEvent> findDue(OffsetDateTime now, Pageable pageable);
}
