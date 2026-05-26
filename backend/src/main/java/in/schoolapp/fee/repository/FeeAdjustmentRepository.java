package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeAdjustmentRepository extends JpaRepository<FeeAdjustment, UUID> {

    List<FeeAdjustment> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    List<FeeAdjustment> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    /** All adjustments for a student — derived via invoice join in service. */
    List<FeeAdjustment> findBySchoolIdAndInvoiceIdInOrderByCreatedAtAsc(UUID schoolId, List<UUID> invoiceIds);
}
