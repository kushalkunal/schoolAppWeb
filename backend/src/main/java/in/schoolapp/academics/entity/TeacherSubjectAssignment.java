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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Who teaches what: a single (teacher, subject, section, academic year) tuple. Powers
 * per-teacher marks-entry authorisation — once wired, a SUBJECT_TEACHER can only submit marks
 * for sections+subjects they appear against in this table.
 */
@Entity
@Table(name = "teacher_subject_assignments",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"staff_id", "subject_id", "section_id", "academic_year_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TeacherSubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
