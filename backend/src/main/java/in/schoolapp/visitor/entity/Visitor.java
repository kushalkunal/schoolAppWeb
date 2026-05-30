package in.schoolapp.visitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Slice 34 — gate visitor log. One row per visitor in/out.
 */
@Entity
@Table(name = "visitors")
@Getter @Setter @NoArgsConstructor
public class Visitor {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String purpose;

    @Column(name = "host_staff_id")
    private UUID hostStaffId;

    @Column(name = "host_student_id")
    private UUID hostStudentId;

    /** True when this visit is to collect a student — triggers guardian authorization (audit #16). */
    @Column(name = "student_pickup", nullable = false)
    private boolean studentPickup = false;

    /** null = not a pickup; true = matched a registered guardian; false = allowed via override. */
    @Column(name = "pickup_authorized")
    private Boolean pickupAuthorized;

    @Column(name = "pickup_override_reason", columnDefinition = "TEXT")
    private String pickupOverrideReason;

    @Column(name = "badge_number", length = 40)
    private String badgeNumber;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "in_at", nullable = false)
    private OffsetDateTime inAt = OffsetDateTime.now();

    @Column(name = "out_at")
    private OffsetDateTime outAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_id")
    private UUID createdById;
}
