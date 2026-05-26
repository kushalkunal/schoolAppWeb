package in.schoolapp.fee.structure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fee_structure_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "academic_year_id", "name"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class FeeStructureVersion {

    public enum Status { DRAFT, ACTIVE, ARCHIVED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;
}
