package in.schoolapp.cafeteria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "cafeteria_order_items")
@Getter @Setter @NoArgsConstructor
public class OrderItem {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_paise", nullable = false)
    private long unitPricePaise;

    @Column(name = "line_total_paise", nullable = false)
    private long lineTotalPaise;
}
