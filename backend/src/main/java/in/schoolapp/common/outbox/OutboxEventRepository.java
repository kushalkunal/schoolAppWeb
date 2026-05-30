package in.schoolapp.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Pull a batch of due, unprocessed events and lock them with {@code FOR UPDATE SKIP LOCKED}
     * so concurrent pollers on other instances claim disjoint sets instead of double-processing
     * the same rows (audit #5 — multi-instance safety). Must run inside a transaction (see
     * {@link OutboxClaimer}) so the locks are held until the claim commits.
     */
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE processed_at IS NULL
          AND next_attempt_at <= :now
        ORDER BY next_attempt_at ASC
        LIMIT :batch
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findDueForUpdate(@Param("now") OffsetDateTime now, @Param("batch") int batch);
}
