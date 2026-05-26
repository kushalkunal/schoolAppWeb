package in.schoolapp.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "asset_tag"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class InventoryItem {

    public enum Status { AVAILABLE, ISSUED, UNDER_MAINTENANCE, LOST, RETIRED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "asset_tag", length = 60)
    private String assetTag;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "unit_cost_paise")
    private Long unitCostPaise;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_invoice_url", columnDefinition = "TEXT")
    private String purchaseInvoiceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
