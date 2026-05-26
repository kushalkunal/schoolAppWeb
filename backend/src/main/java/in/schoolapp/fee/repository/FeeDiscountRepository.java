package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FeeDiscountRepository extends JpaRepository<FeeDiscount, UUID> {

    /**
     * Active discounts that apply to the given student on the given date. {@code feeHeadId}
     * is matched OR the row's {@code fee_head_id} is NULL (blanket).
     */
    @Query("""
        SELECT d FROM FeeDiscount d
        WHERE d.schoolId = :schoolId
          AND d.studentId = :studentId
          AND d.active = true
          AND d.validFrom <= :asOf
          AND (d.validUntil IS NULL OR d.validUntil >= :asOf)
          AND (d.feeHeadId IS NULL OR d.feeHeadId = :feeHeadId)
        """)
    List<FeeDiscount> findApplicable(@Param("schoolId") UUID schoolId,
                                     @Param("studentId") UUID studentId,
                                     @Param("feeHeadId") UUID feeHeadId,
                                     @Param("asOf") LocalDate asOf);

    List<FeeDiscount> findBySchoolIdAndStudentIdOrderByCreatedAtDesc(UUID schoolId, UUID studentId);
}
