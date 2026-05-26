package in.schoolapp.cashrecon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_reconciliations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "closed_on_date", "closed_by_id"}))
@Getter @Setter @NoArgsConstructor
public class CashReconciliation {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "closed_by_id", nullable = false)
    private UUID closedById;

    @Column(name = "closed_on_date", nullable = false)
    private LocalDate closedOnDate;

    @Column(name = "expected_cash_paise",   nullable = false) private long expectedCashPaise;
    @Column(name = "expected_upi_paise",    nullable = false) private long expectedUpiPaise;
    @Column(name = "expected_cheque_paise", nullable = false) private long expectedChequePaise;
    @Column(name = "expected_other_paise",  nullable = false) private long expectedOtherPaise;

    @Column(name = "counted_cash_paise",   nullable = false) private long countedCashPaise;
    @Column(name = "counted_upi_paise",    nullable = false) private long countedUpiPaise;
    @Column(name = "counted_cheque_paise", nullable = false) private long countedChequePaise;
    @Column(name = "counted_other_paise",  nullable = false) private long countedOtherPaise;

    @Column(name = "variance_paise", nullable = false)
    private long variancePaise;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "closed_at", nullable = false)
    private OffsetDateTime closedAt = OffsetDateTime.now();
}
