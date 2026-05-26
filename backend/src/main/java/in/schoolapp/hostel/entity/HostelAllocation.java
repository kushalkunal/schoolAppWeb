package in.schoolapp.hostel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hostel_allocations")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class HostelAllocation {

    public enum Status { ACTIVE, VACATED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "allocated_from", nullable = false)
    private LocalDate allocatedFrom;

    @Column(name = "allocated_until")
    private LocalDate allocatedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "vacated_at")
    private OffsetDateTime vacatedAt;

    @Column(name = "vacated_reason", columnDefinition = "TEXT")
    private String vacatedReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;
}
