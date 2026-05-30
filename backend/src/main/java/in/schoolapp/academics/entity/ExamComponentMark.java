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
 * Teacher's component-level marks entry for one student in one component.
 * <p>
 * Upsert-keyed on {@code (config_id, student_id)} so re-submitting the same grid entry
 * overwrites cleanly — supports teacher corrections and mobile offline sync.
 * <p>
 * After every save {@link in.schoolapp.academics.ComponentMarksService} rolls up the component
 * totals into {@link ExamMark} so the existing {@link in.schoolapp.academics.ReportCardService}
 * remains compatible without modification.
 */
@Entity
@Table(name = "exam_component_marks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"config_id", "student_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ExamComponentMark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "config_id", nullable = false, updatable = false)
    private UUID configId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    /** Null means the teacher has not yet entered a value. */
    @Column(precision = 6, scale = 2)
    private BigDecimal obtained;

    @Column(name = "is_absent", nullable = false)
    private boolean absent = false;

    @Column(name = "is_draft", nullable = false)
    private boolean draft = true;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "entered_by")
    private UUID enteredBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
