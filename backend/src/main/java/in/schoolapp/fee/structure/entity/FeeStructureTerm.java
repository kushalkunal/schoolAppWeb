package in.schoolapp.fee.structure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fee_structure_terms",
       uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "term_number"}))
@Getter @Setter @NoArgsConstructor
public class FeeStructureTerm {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "term_number", nullable = false)
    private int termNumber;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
}
