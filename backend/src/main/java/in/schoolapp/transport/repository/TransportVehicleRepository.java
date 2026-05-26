package in.schoolapp.transport.repository;

import in.schoolapp.transport.entity.TransportVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransportVehicleRepository extends JpaRepository<TransportVehicle, UUID> {
    List<TransportVehicle> findBySchoolIdAndActiveTrueOrderByRegistrationNoAsc(UUID schoolId);
    List<TransportVehicle> findByRouteId(UUID routeId);
}
