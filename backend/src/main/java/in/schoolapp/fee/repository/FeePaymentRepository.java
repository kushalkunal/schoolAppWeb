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
}
