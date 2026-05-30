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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An assessment event (e.g. "Unit Test 1", "Term 1 Final"). Scoped to an academic year so the
 * same exam name can exist across years without collision. Publishing an exam
 * ({@code isPublished=true}) is what finalises the marks and permits report-card generation.
 * <p>
 * {@code classId} / {@code sectionId} optionally scope the exam to a specific class or section.
 * {@code resultStatus} tracks the result lifecycle: DRAFT → READY → PUBLISHED.
 */
@Entity
@Table(name = "exams")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", length = 20)
    private ExamType examType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Optional — narrows the exam to one class. Null means school-wide. */
    @Column(name = "class_id")
    private UUID classId;

    /** Optional — narrows the exam to one section. Null means all sections of the class. */
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 15)
    private ResultStatus resultStatus = ResultStatus.DRAFT;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
