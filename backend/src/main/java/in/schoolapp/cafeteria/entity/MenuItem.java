package in.schoolapp.cafeteria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cafeteria_menu_items")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class MenuItem {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String category;        // BREAKFAST | LUNCH | SNACK | DINNER | BEVERAGE | OTHER

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false)
    private boolean available = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
