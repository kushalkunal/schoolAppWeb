package in.schoolapp.timetable.entity;

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

/** A physical room (classroom, lab, hall…) that timetable slots can be scheduled into. */
@Entity
@Table(name = "classrooms",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "name"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 30)
    private String code;

    @Column(length = 80)
    private String building;

    private Integer capacity;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType = "CLASSROOM";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
