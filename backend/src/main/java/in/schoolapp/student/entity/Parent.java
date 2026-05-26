package in.schoolapp.student.entity;

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
 * Parent/Guardian contact. Phone is unique per tenant — this is what powers sibling detection:
 * when creating a student with a parent phone that already exists, we link to the existing
 * Parent record and the two students become siblings.
 */
@Entity
@Table(name = "parents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "phone"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Parent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 255)
    private String email;

    /** FATHER | MOTHER | GUARDIAN — hint for template rendering ("Dear Mr. X" vs "Dear Ms. Y"). */
    @Column(name = "relation_type", length = 20)
    private String relationType;

    @Column(length = 100)
    private String occupation;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
