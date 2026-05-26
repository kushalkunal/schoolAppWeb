package in.schoolapp.homework.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A student's submission for one assignment. Unique on (assignment, student) so re-submission
 * updates rather than dupes.
 *
 * <p>Doesn't extend BaseEntity because the {@code created_by_id} concept doesn't apply
 * (submissions are by the student, not a staff member).
 */
@Entity
@Table(
    name = "homework_submissions",
    uniqueConstraints = @UniqueConstraint(name = "uq_hw_sub_assignment_student",
        columnNames = {"assignment_id", "student_id"})
)
@Getter
@Setter
public class HomeworkSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "submission_text", columnDefinition = "TEXT")
    private String submissionText;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Column(name = "teacher_remark", columnDefinition = "TEXT")
    private String teacherRemark;

    @Column(length = 10)
    private String grade;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;
}
