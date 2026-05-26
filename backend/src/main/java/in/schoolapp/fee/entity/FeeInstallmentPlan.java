package in.schoolapp.fee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A plan that splits a parent {@link FeeInvoice} into N child invoices. The parent's status
 * flips to {@link InvoiceStatus#SUPERSEDED} so it disappears from dashboards / defaulters
 * lists while remaining for audit traceability.
 */
@Entity
@Table(name = "fee_installment_plans")
@Getter
@Setter
@NoArgsConstructor
public class FeeInstallmentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "parent_invoice_id", nullable = false, unique = true)
    private UUID parentInvoiceId;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;
}
