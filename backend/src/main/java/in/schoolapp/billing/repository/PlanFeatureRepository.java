package in.schoolapp.billing.repository;

import in.schoolapp.billing.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, PlanFeature.Id> {

    @Query("SELECT pf.id.featureKey FROM PlanFeature pf WHERE pf.id.planId = :planId")
    Set<String> findFeatureKeysByPlanId(UUID planId);

    List<PlanFeature> findById_PlanId(UUID planId);
}
