package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, UUID> {

    Optional<FeeInvoice> findByIdAndSchoolId(UUID id, UUID schoolId);

    /** FIFO pending invoices for a student — used to apply payments. */
    List<FeeInvoice> findByStudentIdAndStatusInOrderByDueDateAscCreatedAtAsc(
        UUID studentId, List<InvoiceStatus> statuses);

    List<FeeInvoice> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    /**
     * Slice 33d — fee-due reminder cron source. Returns invoices PENDING/PARTIAL whose
     * due date is exactly {@code dueDate} (used for "due in 3 / 1 / 0 days" notifications).
     * Scoped to a single tenant so the cron can fan-out per-school.
     */
    List<FeeInvoice> findBySchoolIdAndStatusInAndDueDate(
        UUID schoolId, List<InvoiceStatus> statuses, LocalDate dueDate);

    /**
     * Slice 33d — overdue reminder cron source. Returns invoices still PENDING/PARTIAL with
     * a due date strictly before {@code asOf}. The cron groups by student before notifying so
     * a defaulter with five overdue invoices gets one message, not five.
     */
    List<FeeInvoice> findBySchoolIdAndStatusInAndDueDateBefore(
        UUID schoolId, List<InvoiceStatus> statuses, LocalDate asOf);

    /**
     * Defaulters dashboard: students with outstanding invoices past their due date. Aggregated
     * per student — one row per student with their oldest overdue date and total outstanding.
     */
    @Query(value = """
        SELECT student_id,
               SUM(amount_due_paise - amount_paid_paise) AS outstanding_paise,
               MIN(due_date)                             AS oldest_due_date,
               COUNT(*)                                  AS invoice_count
        FROM fee_invoices
        WHERE school_id = :schoolId
          AND status IN ('PENDING', 'PARTIAL')
          AND due_date IS NOT NULL
          AND due_date < :asOf
        GROUP BY student_id
        ORDER BY outstanding_paise DESC
        """, nativeQuery = true)
    Page<DefaulterRow> findDefaulters(@Param("schoolId") UUID schoolId,
                                      @Param("asOf") LocalDate asOf,
                                      Pageable pageable);

    @Query(value = """
        SELECT COALESCE(SUM(amount_due_paise - amount_paid_paise), 0)
        FROM fee_invoices
        WHERE school_id = :schoolId
          AND status IN ('PENDING', 'PARTIAL')
        """, nativeQuery = true)
    long sumOutstandingBySchool(@Param("schoolId") UUID schoolId);

    @Query(value = """
        SELECT COALESCE(SUM(amount_due_paise - amount_paid_paise), 0)
        FROM fee_invoices
        WHERE school_id = :schoolId
          AND status IN ('PENDING', 'PARTIAL')
          AND due_date < :asOf
        """, nativeQuery = true)
    long sumOverdueBySchool(@Param("schoolId") UUID schoolId, @Param("asOf") LocalDate asOf);

    @Query(value = """
        SELECT COUNT(DISTINCT student_id)
        FROM fee_invoices
        WHERE school_id = :schoolId
          AND status IN ('PENDING', 'PARTIAL')
        """, nativeQuery = true)
    long countStudentsWithDues(@Param("schoolId") UUID schoolId);

    @Query(value = """
        SELECT COALESCE(SUM(amount_due_paise - amount_paid_paise), 0)
        FROM fee_invoices
        WHERE student_id = :studentId
          AND status IN ('PENDING', 'PARTIAL')
        """, nativeQuery = true)
    long sumOutstandingByStudent(@Param("studentId") UUID studentId);

    /** Projection interface for the defaulters native query. */
    interface DefaulterRow {
        UUID getStudentId();
        Long getOutstandingPaise();
        LocalDate getOldestDueDate();
        Long getInvoiceCount();
    }

    /**
     * Students with at least one PENDING/PARTIAL invoice whose due date is exactly
     * {@code dueDate} — used by the reminder scheduler. Returns distinct studentIds so a
     * student with two invoices due the same day gets one reminder.
     */
    @Query(value = """
        SELECT DISTINCT student_id
        FROM fee_invoices
        WHERE school_id = :schoolId
          AND status IN ('PENDING', 'PARTIAL')
          AND due_date = :dueDate
        """, nativeQuery = true)
    List<UUID> findStudentsWithInvoicesDueOn(@Param("schoolId") UUID schoolId,
                                             @Param("dueDate") LocalDate dueDate);

    /**
     * Slice 15: invoices that are PENDING/PARTIAL, have a fee head with a non-zero
     * {@code late_fee_paise_per_day}, and whose {@code due_date + grace_days} is on or
     * before today. The late-fee cron iterates this list every morning.
     *
     * <p>{@code last_late_fee_applied_at} guards against double-application within the
     * same day if the cron is re-invoked or the job runs after a manual trigger.
     */
    @Query(value = """
        SELECT fi.*
        FROM fee_invoices fi
        JOIN fee_heads fh ON fh.id = fi.fee_head_id
        WHERE fi.status IN ('PENDING', 'PARTIAL')
          AND fh.late_fee_paise_per_day > 0
          AND fi.due_date IS NOT NULL
          AND fi.due_date + (fh.late_fee_grace_days || ' days')::interval <= :today
          AND (
            fi.last_late_fee_applied_at IS NULL
            OR fi.last_late_fee_applied_at < (:today::timestamp)
          )
        """, nativeQuery = true)
    List<FeeInvoice> findInvoicesEligibleForLateFee(@Param("today") LocalDate today);
}
