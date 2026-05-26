package in.schoolapp.fee.structure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "fee_structure_rows",
       uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "class_id", "fee_head_id", "term_number"}))
@Getter @Setter @NoArgsConstructor
public class FeeStructureRow {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "fee_head_id", nullable = false)
    private UUID feeHeadId;

    /** NULL = annual (single billing across the academic year). */
    @Column(name = "term_number")
    private Integer termNumber;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "is_optional", nullable = false)
    private boolean optional = false;
}
