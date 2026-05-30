package in.schoolapp.school.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Staff member of a school. Login subject: OTP + JWT authenticates a Staff, which carries the
 * {@code schoolId} claim through every request.
 * <p>
 * Not extending {@link in.schoolapp.common.BaseEntity} because we want the auditing columns
 * separately named and do not want {@code created_by_id} here (Staff are created during
 * onboarding before any authenticated user exists).
 */
@Entity
@Table(name = "staff",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "phone"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /**
     * Normalised 10-digit Indian mobile. Globally unique (partial unique index).
     * Nullable since email-only signups may omit it.
     */
    @Column(length = 15)
    private String phone;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StaffRole role;

    @Column(length = 10)
    private String gender;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // ---------- Slice 35 — password login ----------

    /** BCrypt hash. Nullable until the user opts into password login (post-OTP). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** Phone or email verified via OTP. Required before password can be set. */
    @Column(name = "identifier_verified", nullable = false)
    private boolean identifierVerified = false;

    @Column(name = "identifier_verified_at")
    private OffsetDateTime identifierVerifiedAt;

    @Column(name = "password_set_at")
    private OffsetDateTime passwordSetAt;

    /** Failed password-login counter — resets on success. */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    /** Account lockout after too many failed password attempts. */
    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    /**
     * Set to true when an admin pre-sets a temp password (teacher invite flow).
     * Cleared to false once the teacher changes their own password.
     */
    @Column(name = "must_reset_password", nullable = false)
    private boolean mustResetPassword = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String displayName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }
}
