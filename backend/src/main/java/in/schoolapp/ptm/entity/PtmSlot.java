package in.schoolapp.ptm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ptm_slots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "slot_date", "start_time"}))
@Getter @Setter @NoArgsConstructor
public class PtmSlot {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "teacher_id", nullable = false) private UUID teacherId;
    @Column(name = "slot_date", nullable = false) private LocalDate slotDate;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Column(nullable = false) private int capacity = 1;
    @Column(name = "booked_count", nullable = false) private int bookedCount;
    /** Optional: the class/section this PTM slot is for. When set, all parents in that section receive an announcement. */
    @Column(name = "section_id") private UUID sectionId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
