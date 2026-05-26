package in.schoolapp.student.entity;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Binds a Student to exactly one Section for a given academic year. A Student accumulates one
 * Enrollment per year as they progress through grades — {@code findByStudentId} ordered by year
 * reconstructs their timeline (LLD §7.2 / gap analysis §5.1).
 */
@Entity
@Table(name = "student_enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_year_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StudentEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "roll_number")
    private Integer rollNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "leaving_date")
    private LocalDate leavingDate;

    @Column(name = "leaving_reason", columnDefinition = "TEXT")
    private String leavingReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
