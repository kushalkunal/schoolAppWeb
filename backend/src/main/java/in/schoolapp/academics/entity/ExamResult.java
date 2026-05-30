package in.schoolapp.academics.entity;

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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Auto-computed exam result for one student in one exam.
 * <p>
 * Computed by {@link in.schoolapp.academics.ResultService#computeForSection} after the teacher
 * submits final marks. Unique on {@code (exam_id, student_id)} so re-computation is a safe
 * idempotent upsert — fixing a mark and re-running updates this row in place.
 * <p>
 * {@code rankInSection} uses dense rank: two students with equal percentages share rank N and
 * the next rank is N+1 (not N+2).
 */
@Entity
@Table(name = "exam_results",
       uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "student_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ExamResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(name = "total_max", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalMax;

    @Column(name = "total_obtained", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalObtained;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(length = 5)
    private String grade;

    @Column(name = "rank_in_section")
    private Integer rankInSection;

    @Column(name = "is_pass", nullable = false)
    private boolean pass = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ResultStatus status = ResultStatus.DRAFT;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
