package in.schoolapp.hostel.entity;

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
@Table(name = "hostel_rooms",
       uniqueConstraints = @UniqueConstraint(columnNames = {"hostel_id", "room_number"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class HostelRoom {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "hostel_id", nullable = false)
    private UUID hostelId;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    private Integer floor;

    @Column(name = "room_type", nullable = false, length = 15)
    private String roomType;     // SINGLE | DOUBLE | TRIPLE | DORM

    @Column(nullable = false)
    private int capacity;

    @Column(name = "current_occupancy", nullable = false)
    private int currentOccupancy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean hasVacancy() { return currentOccupancy < capacity; }
}
