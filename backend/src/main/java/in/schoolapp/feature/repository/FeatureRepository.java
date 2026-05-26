package in.schoolapp.feature.repository;

import in.schoolapp.feature.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureRepository extends JpaRepository<Feature, String> {
}
