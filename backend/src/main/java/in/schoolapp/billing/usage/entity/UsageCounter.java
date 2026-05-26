package in.schoolapp.billing.usage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Counter row per (school, metric, period_key). Period_key examples:
 * {@code "2026-04"} for monthly metrics, {@code "ALL"} for lifetime metrics.
 */
@Entity
@Table(name = "usage_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsageCounter {

    @EmbeddedId
    private Id id;

    @Column(name = "count_value", nullable = false)
    private long countValue;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "school_id", nullable = false)
        private UUID schoolId;

        @Column(name = "metric", nullable = false, length = 40)
        private String metric;

        @Column(name = "period_key", nullable = false, length = 10)
        private String periodKey;
    }
}
