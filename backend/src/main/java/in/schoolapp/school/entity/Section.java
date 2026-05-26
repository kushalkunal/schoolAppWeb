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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sections",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "class_id", "academic_year_id", "name"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(nullable = false, length = 10)
    private String name;

    /** Nullable — set once staff are on-boarded. */
    @Column(name = "class_teacher_id")
    private UUID classTeacherId;

    @Column(name = "max_strength")
    private Integer maxStrength = 50;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
