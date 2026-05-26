package in.schoolapp.ptm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ptm_bookings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "student_id"}))
@Getter @Setter @NoArgsConstructor
public class PtmBooking {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "slot_id", nullable = false) private UUID slotId;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(nullable = false, length = 15) private String status = "CONFIRMED";
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
