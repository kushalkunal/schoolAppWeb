package in.schoolapp.cafeteria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only ledger of every wallet mutation. Provides an audit trail and lets us
 * reconstruct {@code Wallet.balance_paise} from scratch if it ever drifts.
 */
@Entity
@Table(name = "cafeteria_wallet_transactions")
@Getter @Setter @NoArgsConstructor
public class WalletTransaction {

    public enum Type { TOPUP, DEBIT, REFUND }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 10)
    private Type txnType;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "ref_order_id")
    private UUID refOrderId;

    @Column(name = "balance_after_paise", nullable = false)
    private long balanceAfterPaise;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;
}
