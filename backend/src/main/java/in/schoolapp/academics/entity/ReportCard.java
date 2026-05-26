package in.schoolapp.academics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Generated report card — aggregate marks + percentage + grade + class rank + PDF URL +
 * WhatsApp delivery status. One card per (student, exam). Regeneration is safe (idempotent
 * unique) and useful — re-running after corrections updates the same row.
 */
@Entity
@Table(name = "report_cards",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "exam_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ReportCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "total_marks", precision = 7, scale = 2)
    private BigDecimal totalMarks;

    @Column(name = "obtained_marks", precision = 7, scale = 2)
    private BigDecimal obtainedMarks;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(length = 5)
    private String grade;

    @Column(name = "rank_in_class")
    private Integer rankInClass;

    @Column(name = "teacher_remarks", columnDefinition = "TEXT")
    private String teacherRemarks;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "wa_sent_at")
    private OffsetDateTime waSentAt;

    @Column(name = "wa_delivered_at")
    private OffsetDateTime waDeliveredAt;

    @Column(name = "wa_read_at")
    private OffsetDateTime waReadAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
