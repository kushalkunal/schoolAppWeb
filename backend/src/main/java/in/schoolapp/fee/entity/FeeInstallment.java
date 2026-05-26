package in.schoolapp.fee.entity;

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

import java.time.LocalDate;
import java.util.UUID;

/**
 * One leg of an installment plan. Each row maps to a fresh {@link FeeInvoice} so existing
 * payment + reminder machinery works unchanged: collecting against a child invoice updates
 * its status independently of its siblings.
 */
@Entity
@Table(name = "fee_installments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "sequence_no"}))
@Getter
@Setter
@NoArgsConstructor
public class FeeInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "child_invoice_id", nullable = false)
    private UUID childInvoiceId;

    /** 1-based; matches what the parent sees in their receipt history. */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;
}
