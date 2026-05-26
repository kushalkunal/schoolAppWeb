package in.schoolapp.admissions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "admission_test_scores",
       uniqueConstraints = @UniqueConstraint(columnNames = {"admission_id", "subject_name"}))
@Getter
@Setter
@NoArgsConstructor
public class AdmissionTestScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    @Column(name = "max_marks", nullable = false)
    private int maxMarks;

    @Column(name = "obtained_marks", nullable = false)
    private int obtainedMarks;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
