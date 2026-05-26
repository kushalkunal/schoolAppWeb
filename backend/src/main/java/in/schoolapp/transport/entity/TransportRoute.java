package in.schoolapp.transport.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "transport_routes",
    uniqueConstraints = @UniqueConstraint(name = "uq_transport_route_school_name",
        columnNames = {"school_id", "name"}))
@Getter
@Setter
public class TransportRoute extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** Ordered stops as JSONB: [{order, name, time}, ...] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> stops = new ArrayList<>();

    @Column(name = "fare_paise", nullable = false)
    private long farePaise;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
