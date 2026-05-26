package in.schoolapp.expense.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "expense_categories",
       uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "name"}))
@Getter @Setter @NoArgsConstructor
public class ExpenseCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false) private boolean active = true;
}
