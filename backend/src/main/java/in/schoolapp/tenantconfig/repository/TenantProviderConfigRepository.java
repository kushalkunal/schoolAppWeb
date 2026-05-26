package in.schoolapp.tenantconfig.repository;

import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.entity.TenantProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantProviderConfigRepository extends JpaRepository<TenantProviderConfig, UUID> {

    Optional<TenantProviderConfig> findBySchoolIdAndConcern(UUID schoolId, ProviderConcern concern);

    List<TenantProviderConfig> findBySchoolId(UUID schoolId);
}
