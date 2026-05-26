package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeInstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeeInstallmentPlanRepository extends JpaRepository<FeeInstallmentPlan, UUID> {

    Optional<FeeInstallmentPlan> findByParentInvoiceId(UUID parentInvoiceId);
}
