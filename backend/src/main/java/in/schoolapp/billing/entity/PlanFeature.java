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
 * Link row: which features each plan includes. Composite primary key (plan_id, feature_key)
 * matches the DDL in Flyway V6.
 */
@Entity
@Table(name = "plan_features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {

    @EmbeddedId
    private Id id;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "plan_id", nullable = false)
        private UUID planId;

        @Column(name = "feature_key", nullable = false, length = 80)
        private String featureKey;
    }
}
