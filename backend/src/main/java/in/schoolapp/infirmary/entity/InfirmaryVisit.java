package in.schoolapp.infirmary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "infirmary_visits")
@Getter @Setter @NoArgsConstructor
public class InfirmaryVisit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "visited_at", nullable = false) private OffsetDateTime visitedAt = OffsetDateTime.now();
    @Column(nullable = false, columnDefinition = "TEXT") private String complaint;
    @Column(columnDefinition = "TEXT") private String treatment;
    @Column(name = "medicine_given", columnDefinition = "TEXT") private String medicineGiven;
    @Column(name = "temperature_c", precision = 4, scale = 1) private BigDecimal temperatureC;
    private Integer pulse;
    @Column(name = "sent_home", nullable = false) private boolean sentHome;
    @Column(name = "parent_notified", nullable = false) private boolean parentNotified;
    @Column(name = "recorded_by_id", nullable = false) private UUID recordedById;
    @Column(columnDefinition = "TEXT") private String notes;
}
