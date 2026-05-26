package in.schoolapp.transport.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_transport_assignments",
    uniqueConstraints = @UniqueConstraint(name = "uq_sta_student_start",
        columnNames = {"student_id", "start_date"}))
@Getter
@Setter
public class StudentTransportAssignment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "stop_name", length = 120)
    private String stopName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
