package in.schoolapp.admissions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "admissions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdmissionStatus status = AdmissionStatus.ENQUIRY;

    @Column(name = "parent_name", length = 200)
    private String parentName;

    @Column(name = "parent_phone", nullable = false, length = 20)
    private String parentPhone;

    @Column(name = "parent_email", length = 255)
    private String parentEmail;

    @Column(name = "student_first_name", nullable = false, length = 100)
    private String studentFirstName;

    @Column(name = "student_last_name", length = 100)
    private String studentLastName;

    @Column(name = "student_date_of_birth")
    private LocalDate studentDateOfBirth;

    @Column(name = "student_gender", length = 10)
    private String studentGender;

    @Column(name = "intended_class", nullable = false, length = 50)
    private String intendedClass;

    @Column(name = "intended_section", length = 20)
    private String intendedSection;

    @Column(name = "intended_academic_year", length = 20)
    private String intendedAcademicYear;

    @Column(length = 40)
    private String source;

    @Column(name = "referrer_name", length = 200)
    private String referrerName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Test
    @Column(name = "test_scheduled_at")
    private OffsetDateTime testScheduledAt;

    @Column(name = "test_venue", columnDefinition = "TEXT")
    private String testVenue;

    @Column(name = "test_total_marks")
    private Integer testTotalMarks;

    @Column(name = "test_obtained_marks")
    private Integer testObtainedMarks;

    @Column(name = "test_remarks", columnDefinition = "TEXT")
    private String testRemarks;

    // Offer
    @Column(name = "offer_letter_url", columnDefinition = "TEXT")
    private String offerLetterUrl;

    @Column(name = "offer_issued_at")
    private OffsetDateTime offerIssuedAt;

    @Column(name = "offer_accepted_at")
    private OffsetDateTime offerAcceptedAt;

    @Column(name = "offer_declined_at")
    private OffsetDateTime offerDeclinedAt;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    // Enrolment outcome
    @Column(name = "enrolled_student_id")
    private UUID enrolledStudentId;

    @Column(name = "enrolled_at")
    private OffsetDateTime enrolledAt;

    @Column(name = "assigned_to_id")
    private UUID assignedToId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    public String studentDisplayName() {
        return studentLastName != null && !studentLastName.isBlank()
            ? (studentFirstName + " " + studentLastName).trim()
            : studentFirstName;
    }
}
