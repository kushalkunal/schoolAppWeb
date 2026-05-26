package in.schoolapp.billing.entity;

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
import java.util.UUID;

/**
 * Numeric ceiling for a plan × metric pair. {@code limitValue = -1} == unlimited.
 */
@Entity
@Table(name = "plan_limits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimit {

    @EmbeddedId
    private Id id;

    @Column(name = "limit_value", nullable = false)
    private long limitValue;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "plan_id", nullable = false)
        private UUID planId;

        @Column(name = "metric", nullable = false, length = 40)
        private String metric;
    }
}
