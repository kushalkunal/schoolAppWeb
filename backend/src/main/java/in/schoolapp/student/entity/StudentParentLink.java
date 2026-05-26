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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Student ↔ Parent many-to-many link. {@code isPrimary=true} marks the contact used for
 * notifications (fee receipts, absence alerts). Exactly one primary per student is enforced in
 * service code, not at the DB layer.
 */
@Entity
@Table(name = "student_parent_links",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "parent_id", "relation"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StudentParentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParentRelation relation;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
