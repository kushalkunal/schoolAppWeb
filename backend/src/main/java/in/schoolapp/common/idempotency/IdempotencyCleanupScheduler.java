package in.schoolapp.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Daily 04:00 IST sweep of expired {@code idempotency_keys} rows. The 24-hour TTL means a
 * single day's traffic accumulates in the table; without this job it'd grow unbounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final IdempotencyKeyRepository repository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void cleanup() {
        int deleted = repository.deleteExpired(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("IdempotencyCleanupScheduler removed {} expired idempotency_keys rows", deleted);
        }
    }
}
