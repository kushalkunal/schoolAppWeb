package in.schoolapp.school.entity;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A one-day teacher-substitution record. The unique key is {@code (section_id, assigned_date)}
 * — two substitutes cannot be scheduled for the same class on the same day. Gap §5.5.
 */
@Entity
@Table(name = "substitute_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"section_id", "assigned_date"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SubstituteAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "absent_teacher_id", nullable = false)
    private UUID absentTeacherId;

    @Column(name = "substitute_id", nullable = false)
    private UUID substituteId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
