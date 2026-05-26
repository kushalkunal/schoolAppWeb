package in.schoolapp.expense.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "expenses")
@Getter @Setter @NoArgsConstructor
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "amount_paise", nullable = false) private long amountPaise;
    @Column(name = "spent_on", nullable = false) private LocalDate spentOn;
    @Column(length = 120) private String vendor;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "receipt_url", columnDefinition = "TEXT") private String receiptUrl;
    @Column(name = "payment_mode", length = 20) private String paymentMode;
    @Column(name = "recorded_by_id") private UUID recordedById;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
