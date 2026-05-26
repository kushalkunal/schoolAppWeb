package in.schoolapp.tenantconfig.entity;

import in.schoolapp.tenantconfig.ProviderConcern;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One active provider configuration per (school, concern). The {@code config} JSONB holds
 * provider-specific knobs; sensitive fields inside it are AES-GCM encrypted by
 * {@link in.schoolapp.tenantconfig.TenantProviderConfigService} before save and decrypted
 * on read.
 *
 * <p>Not extending {@code BaseEntity} on purpose: platform-admin endpoints write these
 * outside a tenant context (impersonation isn't required to manage a tenant's WhatsApp
 * credentials).
 */
@Entity
@Table(
    name = "tenant_provider_configs",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tpc_school_concern",
        columnNames = {"school_id", "concern"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class TenantProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderConcern concern;

    @Column(nullable = false, length = 40)
    private String provider;

    /**
     * Provider config as a JSONB map. Values for keys listed in
     * {@code TenantProviderConfigService.SENSITIVE_KEYS} are AES-GCM ciphertext on disk;
     * plaintext after {@code decrypt(...)}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
