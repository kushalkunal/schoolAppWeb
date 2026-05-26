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
@Table(name = "hostels", uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "name"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class Hostel {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 10)
    private String gender;          // BOYS | GIRLS | COED

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "warden_staff_id")
    private UUID wardenStaffId;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
