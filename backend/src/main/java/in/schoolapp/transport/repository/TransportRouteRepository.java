package in.schoolapp.transport.repository;

import in.schoolapp.transport.entity.TransportRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransportRouteRepository extends JpaRepository<TransportRoute, UUID> {
    List<TransportRoute> findBySchoolIdAndActiveTrueOrderByNameAsc(UUID schoolId);
}
