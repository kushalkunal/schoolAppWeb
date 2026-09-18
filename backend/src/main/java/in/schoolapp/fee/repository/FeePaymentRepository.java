package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeePayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {

    Optional<FeePayment> findByIdAndSchoolId(UUID id, UUID schoolId);

    /** Idempotency lookup for payment-gateway webhooks (Slice 7.5). */
    Optional<FeePayment> findByProviderReference(String providerReference);

    List<FeePayment> findByStudentIdOrderByPaymentDateDesc(UUID studentId);

    /** Tenant-scoped paginated export iterator — keeps memory bounded during streaming. */
    Page<FeePayment> findBySchoolIdOrderByPaymentDateDesc(UUID schoolId, Pageable pageable);

    @Query(value = """
        SELECT COALESCE(SUM(amount_paise), 0)
        FROM fee_payments
        WHERE school_id = :schoolId
          AND payment_date BETWEEN :from AND :to
        """, nativeQuery = true)
    long sumCollectedBetween(@Param("schoolId") UUID schoolId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    @Query(value = """
        SELECT COUNT(*)
        FROM fee_payments
        WHERE school_id = :schoolId
          AND payment_date BETWEEN :from AND :to
        """, nativeQuery = true)
    long countCollectedBetween(@Param("schoolId") UUID schoolId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /**
     * Slice 34 — day-end cash reconciliation. One row per payment mode for the date window;
     * caller bucketises the modes into CASH / UPI / CHEQUE / OTHER and compares against the
     * physical counts the cashier supplies.
     */
    @Query(value = """
        SELECT payment_mode AS mode,
               COALESCE(SUM(amount_paise), 0) AS amount_paise,
               COUNT(*) AS payment_count
        FROM fee_payments
        WHERE school_id = :schoolId
          AND payment_date BETWEEN :from AND :to
        GROUP BY payment_mode
        """, nativeQuery = true)
    List<ModeTotalRow> sumByModeBetween(@Param("schoolId") UUID schoolId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /** Projection for {@link #sumByModeBetween}. */
    interface ModeTotalRow {
        String getMode();
        Long   getAmountPaise();
        Long   getPaymentCount();
    }

    /**
     * Recent payments for the cashier dashboard — inner-joined with students so the
     * student name is available without extra N+1 queries.
     */
    @Query(value = """
        SELECT fp.id                                                        AS paymentId,
               fp.student_id                                               AS studentId,
               s.first_name || ' ' || COALESCE(s.last_name, '')           AS studentName,
               fp.amount_paise                                             AS amountPaise,
               fp.payment_mode                                             AS paymentMode,
               fp.receipt_number                                           AS receiptNumber,
               fp.receipt_pdf_url                                          AS receiptPdfUrl,
               fp.payment_date                                             AS paymentDate,
               st.first_name || ' ' || COALESCE(st.last_name, '')         AS collectedByName
        FROM fee_payments fp
        LEFT JOIN students s ON s.id = fp.student_id
        LEFT JOIN staff st ON st.id = fp.collected_by_id
        WHERE fp.school_id = :schoolId
        ORDER BY fp.created_at DESC
        LIMIT :lim
        """, nativeQuery = true)
    List<RecentPaymentProjection> findRecentBySchool(@Param("schoolId") UUID schoolId,
                                                     @Param("lim") int limit);

    /** Projection for {@link #findRecentBySchool}. */
    interface RecentPaymentProjection {
        UUID   getPaymentId();
        UUID   getStudentId();
        String getStudentName();
        Long   getAmountPaise();
        String getPaymentMode();
        String getReceiptNumber();
        String getReceiptPdfUrl();
        java.time.LocalDate getPaymentDate();
        String getCollectedByName();
    }

    /**
     * Class-wise collection summary over a date range. Joins fee_payments →
     * student_enrollments (current) → school_classes for aggregation.
     */
    @Query(value = """
        SELECT sc.id                                    AS classId,
               sc.name                                  AS className,
               COALESCE(SUM(fp.amount_paise), 0)        AS collectedPaise,
               COUNT(fp.id)                             AS paymentCount,
               COALESCE(SUM(
                   CASE WHEN fi.status IN ('PENDING','PARTIAL')
                        THEN fi.amount_due_paise - fi.amount_paid_paise ELSE 0 END
               ), 0)                                    AS outstandingPaise,
               COUNT(DISTINCT se.student_id)            AS studentCount
        FROM school_classes sc
        JOIN sections sec ON sec.class_id = sc.id
        JOIN student_enrollments se ON se.section_id = sec.id AND se.status = 'ACTIVE'
        LEFT JOIN fee_payments fp ON fp.student_id = se.student_id
            AND fp.school_id = :schoolId
            AND fp.payment_date BETWEEN :from AND :to
        LEFT JOIN fee_invoices fi ON fi.student_id = se.student_id
            AND fi.school_id = :schoolId
        WHERE sc.school_id = :schoolId
        GROUP BY sc.id, sc.name
        ORDER BY sc.name
        """, nativeQuery = true)
    List<ClassCollectionProjection> classWiseReport(@Param("schoolId") UUID schoolId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /** Projection for {@link #classWiseReport}. */
    interface ClassCollectionProjection {
        String getClassId();
        String getClassName();
        Long   getCollectedPaise();
        Long   getPaymentCount();
        Long   getOutstandingPaise();
        Long   getStudentCount();
    }
}
