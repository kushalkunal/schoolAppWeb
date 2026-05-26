package in.schoolapp.cashrecon;

import in.schoolapp.cashrecon.entity.CashReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CashReconciliationRepository extends JpaRepository<CashReconciliation, UUID> {

    List<CashReconciliation> findBySchoolIdOrderByClosedOnDateDesc(UUID schoolId);

    List<CashReconciliation> findBySchoolIdAndClosedOnDate(UUID schoolId, LocalDate date);
}
