package in.schoolapp.feature.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Platform-level catalog of toggleable features. NOT tenant-scoped — does not extend
 * {@code BaseEntity} (no {@code schoolId}). Rows are seeded by Flyway V6 and only updated by
 * the platform team when a new feature is launched.
 */
@Entity
@Table(name = "features")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Feature {

    @Id
    @Column(name = "feature_key", length = 80, nullable = false)
    private String featureKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;

    @Column(length = 40)
    private String category;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
