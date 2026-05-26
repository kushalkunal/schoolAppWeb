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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One student's marks in one subject for one exam. Upsert-keyed on
 * {@code (examId, studentId, subjectId)} per the DB unique constraint so re-submitting the
 * same grid overwrites cleanly — important for teacher corrections and mobile offline sync.
 * <p>
 * {@code isDraft=true} while marks are being entered; setting {@code isDraft=false} finalises
 * them and (typically) blocks further edits except by an admin. {@link #grade} is auto-derived
 * from percentage via {@link in.schoolapp.academics.GradeCalculator} — callers don't compute it.
 */
@Entity
@Table(name = "exam_marks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "student_id", "subject_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ExamMark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "obtained_marks", precision = 6, scale = 2)
    private BigDecimal obtainedMarks;

    @Column(name = "is_absent", nullable = false)
    private boolean absent = false;

    @Column(length = 5)
    private String grade;

    @Column(name = "entered_by_id")
    private UUID enteredById;

    @Column(name = "is_draft", nullable = false)
    private boolean draft = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Returns percentage [0, 100] or null if the student was absent or marks are unset. */
    public Double percentage() {
        if (absent || obtainedMarks == null || maxMarks == null
                || maxMarks.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return obtainedMarks.doubleValue() / maxMarks.doubleValue() * 100.0;
    }
}
