package in.schoolapp.feature.repository;

import in.schoolapp.feature.entity.FeatureOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureOverrideRepository extends JpaRepository<FeatureOverride, UUID> {

    Optional<FeatureOverride> findBySchoolIdAndFeatureKey(UUID schoolId, String featureKey);

    List<FeatureOverride> findBySchoolId(UUID schoolId);
}
