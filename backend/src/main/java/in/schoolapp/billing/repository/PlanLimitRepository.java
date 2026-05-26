package in.schoolapp.billing.repository;

import in.schoolapp.billing.entity.PlanLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanLimitRepository extends JpaRepository<PlanLimit, PlanLimit.Id> {

    Optional<PlanLimit> findById_PlanIdAndId_Metric(UUID planId, String metric);

    List<PlanLimit> findById_PlanId(UUID planId);
}
