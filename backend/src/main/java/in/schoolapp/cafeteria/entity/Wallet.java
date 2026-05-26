package in.schoolapp.cafeteria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cafeteria_wallets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "student_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class Wallet {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise = 0;

    @Column(name = "total_topped_up_paise", nullable = false)
    private long totalToppedUpPaise = 0;

    @Column(name = "total_spent_paise", nullable = false)
    private long totalSpentPaise = 0;

    @Column(name = "last_topped_up_at")
    private OffsetDateTime lastToppedUpAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
