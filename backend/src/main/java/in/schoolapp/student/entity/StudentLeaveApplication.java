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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A parent or CLASS_TEACHER can apply for a student leave.
 * The CLASS_TEACHER (or PRINCIPAL/ADMIN) approves or rejects it.
 * When approved, attendance for the leave days is automatically written as LEAVE.
 */
@Entity
@Table(name = "student_leave_applications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StudentLeaveApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int days;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** The staff member who applied (class teacher) or null if applied via parent portal. */
    @Column(name = "applied_by_staff_id")
    private UUID appliedByStaffId;

    /** The staff member who decided (class teacher, admin, or principal). */
    @Column(name = "decided_by_staff_id")
    private UUID decidedByStaffId;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.SUBMITTED;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum LeaveStatus {
        SUBMITTED,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}
