package in.schoolapp.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Latest at-risk snapshot for a single student — written by
 * {@link in.schoolapp.analytics.detector.AtRiskDetectionService} and read by the dashboard.
 * <p>
 * One row per (school, student); the detector upserts via the unique key. {@code score} is a
 * 0-100 composite; the contributing percentages are kept alongside so the UI can explain
 * <i>why</i> a student is flagged rather than just showing an opaque number.
 */
@Entity
@Table(name = "student_risk_scores",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
public class StudentRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(nullable = false)
    private int score;

    @Column(name = "attendance_pct", precision = 5, scale = 2)
    private BigDecimal attendancePct;

    @Column(name = "fee_outstanding_paise", nullable = false)
    private long feeOutstandingPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "marks_trend", length = 10)
    private MarksTrend marksTrend;

    @Enumerated(EnumType.STRING)
    @Column(name = "top_factor", length = 40)
    private RiskFactor topFactor;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt = OffsetDateTime.now();

    /**
     * 2–3 sentence plain-English narrative describing why this student is at risk. Written
     * by an LLM when one is configured (Slice 19b), or a deterministic template otherwise.
     * Stored in lock-step with the numeric score so the dashboard renders both with no
     * extra LLM call.
     */
    @Column(columnDefinition = "TEXT")
    private String summary;
}
