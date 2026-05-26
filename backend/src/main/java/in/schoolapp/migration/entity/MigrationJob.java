package in.schoolapp.migration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One paper artifact (receipt-book page, attendance register page, etc.) being migrated. The
 * job holds raw OCR text + the LLM's extracted JSON + the human-reviewed JSON — three frozen
 * snapshots so we can re-run reviews if the underlying student records change.
 */
@Entity
@Table(name = "migration_jobs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class MigrationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20, updatable = false)
    private MigrationJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MigrationJobStatus status = MigrationJobStatus.UPLOADED;

    /** URL to the source image — typically a {@code FileStorageService} key for {@code migration/}. */
    @Column(name = "image_url", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String imageUrl;

    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    /** LLM's structured extraction — array of records per the type-specific prompt. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_json", columnDefinition = "jsonb")
    private Object extractedJson;

    /** Human-confirmed/edited version of {@link #extractedJson} — what gets committed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reviewed_json", columnDefinition = "jsonb")
    private Object reviewedJson;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
