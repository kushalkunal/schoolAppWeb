package in.schoolapp.billing.repository;

import in.schoolapp.billing.entity.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

    List<SubscriptionEvent> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
