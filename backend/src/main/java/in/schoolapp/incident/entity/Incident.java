package in.schoolapp.incident.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Getter @Setter @NoArgsConstructor
public class Incident {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(nullable = false, length = 15) private String severity;         // MINOR / MAJOR / SEVERE
    @Column(name = "incident_type", nullable = false, length = 40) private String incidentType;
    @Column(nullable = false) private int points;
    @Column(name = "occurred_on", nullable = false) private LocalDate occurredOn;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "action_taken", columnDefinition = "TEXT") private String actionTaken;
    @Column(name = "reported_by_id", nullable = false) private UUID reportedById;
    @Column(name = "parent_notified", nullable = false) private boolean parentNotified;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
