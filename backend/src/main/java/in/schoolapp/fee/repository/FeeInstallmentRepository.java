package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeInstallment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, UUID> {

    List<FeeInstallment> findByPlanIdOrderBySequenceNo(UUID planId);
}
