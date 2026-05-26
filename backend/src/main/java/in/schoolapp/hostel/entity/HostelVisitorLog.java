package in.schoolapp.hostel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hostel_visitor_logs")
@Getter @Setter @NoArgsConstructor
public class HostelVisitorLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "hostel_id", nullable = false)
    private UUID hostelId;

    @Column(name = "visiting_student_id")
    private UUID visitingStudentId;

    @Column(name = "visitor_name", nullable = false, length = 200)
    private String visitorName;

    @Column(name = "visitor_phone", length = 20)
    private String visitorPhone;

    @Column(length = 50)
    private String relation;

    @Column(name = "id_proof", length = 100)
    private String idProof;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "in_time", nullable = false)
    private OffsetDateTime inTime;

    @Column(name = "out_time")
    private OffsetDateTime outTime;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;
}
