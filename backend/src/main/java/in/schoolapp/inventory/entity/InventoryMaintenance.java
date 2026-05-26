package in.schoolapp.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_maintenance")
@Getter @Setter @NoArgsConstructor
public class InventoryMaintenance {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @Column(name = "cost_paise")
    private Long costPaise;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "performed_by", length = 200)
    private String performedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;
}
