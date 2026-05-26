package in.schoolapp.cafeteria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cafeteria_orders")
@Getter @Setter @NoArgsConstructor
public class CafeteriaOrder {

    public enum Status { PLACED, FULFILLED, CANCELLED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.PLACED;

    @Column(name = "placed_at", nullable = false)
    private OffsetDateTime placedAt = OffsetDateTime.now();

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_id")
    private UUID createdById;
}
