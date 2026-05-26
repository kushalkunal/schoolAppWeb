package in.schoolapp.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Single item issued to a staff member OR a student (CHECK constraint enforces exactly one).
 * {@code returned_at} flips status back to AVAILABLE on the parent item.
 */
@Entity
@Table(name = "inventory_issuances")
@Getter @Setter @NoArgsConstructor
public class InventoryIssuance {

    public enum ReturnCondition { GOOD, DAMAGED, LOST }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "issued_to_staff_id")
    private UUID issuedToStaffId;

    @Column(name = "issued_to_student_id")
    private UUID issuedToStudentId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt = OffsetDateTime.now();

    @Column(name = "issued_by_id")
    private UUID issuedById;

    @Column(name = "expected_return_at")
    private OffsetDateTime expectedReturnAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", length = 20)
    private ReturnCondition returnCondition;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
