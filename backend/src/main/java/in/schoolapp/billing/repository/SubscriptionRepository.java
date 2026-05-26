package in.schoolapp.billing.repository;

import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findBySchoolId(UUID schoolId);

    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    long countByStatus(SubscriptionStatus status);
}
