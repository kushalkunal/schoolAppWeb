package in.schoolapp.expense;

import in.schoolapp.expense.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndSchoolId(UUID id, UUID schoolId);

    Page<Expense> findBySchoolIdOrderBySpentOnDesc(UUID schoolId, Pageable page);

    @Query(value = """
        SELECT COALESCE(SUM(amount_paise), 0)
        FROM expenses
        WHERE school_id = :schoolId
          AND spent_on BETWEEN :from AND :to
        """, nativeQuery = true)
    long sumBetween(@Param("schoolId") UUID schoolId,
                    @Param("from") LocalDate from,
                    @Param("to") LocalDate to);
}
