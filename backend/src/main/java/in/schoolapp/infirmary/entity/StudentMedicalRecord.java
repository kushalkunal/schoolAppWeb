package in.schoolapp.infirmary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-student static medical info — blood group, allergies, chronic conditions.
 * Surfaced on the attendance grid + infirmary screen so staff know what to watch for.
 */
@Entity
@Table(name = "student_medical_records")
@Getter @Setter @NoArgsConstructor
public class StudentMedicalRecord {
    @Id
    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "blood_group", length = 5) private String bloodGroup;
    @Column(columnDefinition = "TEXT") private String allergies;
    @Column(name = "chronic_conditions", columnDefinition = "TEXT") private String chronicConditions;
    @Column(columnDefinition = "TEXT") private String medications;
    @Column(name = "emergency_contact", length = 120) private String emergencyContact;
    @Column(name = "emergency_phone", length = 30) private String emergencyPhone;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();
}
