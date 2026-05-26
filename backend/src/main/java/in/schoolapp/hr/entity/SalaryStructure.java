package in.schoolapp.hr.entity;

import in.schoolapp.school.entity.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Salary template. If {@link #staffId} is null, this row is the role-default — applies to
 * every staff member of that role unless overridden. A staff-specific row shadows the role
 * default for that staff.
 */
@Entity
@Table(name = "salary_structures")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StaffRole role;

    /** NULL = role-default. */
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "basic_paise", nullable = false)
    private long basicPaise;

    @Column(name = "hra_paise", nullable = false)
    private long hraPaise;

    @Column(name = "da_paise", nullable = false)
    private long daPaise;

    @Column(name = "special_allowance_paise", nullable = false)
    private long specialAllowancePaise;

    @Column(name = "other_allowance_paise", nullable = false)
    private long otherAllowancePaise;

    @Column(name = "pf_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal pfPercent = new BigDecimal("12.00");

    @Column(name = "esi_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal esiPercent = new BigDecimal("0.75");

    @Column(name = "professional_tax_paise", nullable = false)
    private long professionalTaxPaise = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    public long grossPaise() {
        return basicPaise + hraPaise + daPaise + specialAllowancePaise + otherAllowancePaise;
    }
}
