package in.schoolapp.hr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable monthly payslip snapshot. Service never updates an existing row — instead it
 * creates a new row with {@code version + 1} for corrections / re-issues.
 */
@Entity
@Table(name = "payslips",
       uniqueConstraints = @UniqueConstraint(columnNames = {
           "school_id", "staff_id", "pay_period_year", "pay_period_month", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "pay_period_year", nullable = false)
    private int payPeriodYear;

    @Column(name = "pay_period_month", nullable = false)
    private int payPeriodMonth;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "working_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal workingDays;

    @Column(name = "leave_days_paid", nullable = false, precision = 5, scale = 2)
    private BigDecimal leaveDaysPaid = BigDecimal.ZERO;

    @Column(name = "leave_days_unpaid", nullable = false, precision = 5, scale = 2)
    private BigDecimal leaveDaysUnpaid = BigDecimal.ZERO;

    @Column(name = "gross_paise", nullable = false)
    private long grossPaise;

    @Column(name = "deductions_paise", nullable = false)
    private long deductionsPaise;

    @Column(name = "net_paise", nullable = false)
    private long netPaise;

    /**
     * JSONB snapshot of the calculation inputs (basic, allowances, deductions list,
     * leave details). Lets the payslip PDF render without re-running the math.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> breakdownJson;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt = OffsetDateTime.now();

    @Column(name = "generated_by_id")
    private UUID generatedById;
}
