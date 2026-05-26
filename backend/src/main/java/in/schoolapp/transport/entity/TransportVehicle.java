package in.schoolapp.transport.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "transport_vehicles",
    uniqueConstraints = @UniqueConstraint(name = "uq_transport_vehicle_school_reg",
        columnNames = {"school_id", "registration_no"}))
@Getter
@Setter
public class TransportVehicle extends BaseEntity {

    @Column(name = "registration_no", nullable = false, length = 40)
    private String registrationNo;

    @Column(name = "driver_staff_id")
    private UUID driverStaffId;

    @Column(nullable = false)
    private int capacity = 30;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
