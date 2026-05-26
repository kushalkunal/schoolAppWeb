package in.schoolapp.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Query("""
        SELECT k FROM IdempotencyKey k
        WHERE k.idempotencyKey = :key
          AND k.method = :method
          AND k.path = :path
          AND ((:tenantId IS NULL AND k.tenantId IS NULL) OR k.tenantId = :tenantId)
        """)
    Optional<IdempotencyKey> findByCompositeKey(String key, String method, String path, UUID tenantId);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :cutoff")
    int deleteExpired(OffsetDateTime cutoff);
}
