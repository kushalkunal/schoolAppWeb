package in.schoolapp.feature.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * Per-school override of a feature's enabled state. The presence of a row wins over the
 * subscription plan's inclusion of the feature; absence falls back to the plan.
 * <p>
 * {@code config} is a JSONB grab-bag for per-school knobs the feature itself interprets —
 * e.g. {@code {"providerProfile": "WATI_PRO"}} for WhatsApp routing overrides.
 */
@Entity
@Table(
    name = "feature_overrides",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_feature_overrides_school_key",
        columnNames = {"school_id", "feature_key"}
    )
)
@Getter
@Setter
public class FeatureOverride extends BaseEntity {

    @Column(name = "feature_key", length = 80, nullable = false)
    private String featureKey;

    @Column(nullable = false)
    private boolean enabled;

    /** Per-feature knobs, JSONB column. Defaults to an empty map. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "updated_by_id")
    private UUID updatedById;
}
