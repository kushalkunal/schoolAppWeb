package in.schoolapp.billing.usage.repository;

import in.schoolapp.billing.usage.entity.UsageCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UsageCounterRepository extends JpaRepository<UsageCounter, UsageCounter.Id> {

    List<UsageCounter> findById_SchoolId(UUID schoolId);

    /**
     * Atomic upsert+increment via Postgres ON CONFLICT — keeps the counter correct under
     * concurrent writes without explicit locking. delta may be negative (rare; for refunds).
     */
    @Modifying
    @Query(value = """
        INSERT INTO usage_counters (school_id, metric, period_key, count_value, updated_at)
        VALUES (:schoolId, :metric, :periodKey, :delta, NOW())
        ON CONFLICT (school_id, metric, period_key)
        DO UPDATE SET count_value = usage_counters.count_value + EXCLUDED.count_value,
                      updated_at  = NOW()
        """, nativeQuery = true)
    void atomicIncrement(@Param("schoolId") UUID schoolId,
                         @Param("metric") String metric,
                         @Param("periodKey") String periodKey,
                         @Param("delta") long delta);
}
