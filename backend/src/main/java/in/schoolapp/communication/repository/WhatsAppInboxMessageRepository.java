package in.schoolapp.communication.repository;

import in.schoolapp.communication.entity.WhatsAppInboxMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WhatsAppInboxMessageRepository extends JpaRepository<WhatsAppInboxMessage, UUID> {

    boolean existsByWaMessageId(String waMessageId);

    /** Teacher's own inbox — all unresolved messages routed to them. */
    Page<WhatsAppInboxMessage> findBySchoolIdAndRoutedToIdOrderByReceivedAtDesc(
        UUID schoolId, UUID routedToId, Pageable pageable);

    /** Principal view — every inbound message for the tenant, including unrouted. */
    Page<WhatsAppInboxMessage> findBySchoolIdOrderByReceivedAtDesc(UUID schoolId, Pageable pageable);

    /** Unrouted (unknown phone) backlog — principals/admins need to triage these. */
    Page<WhatsAppInboxMessage> findBySchoolIdAndRoutedToIdIsNullOrderByReceivedAtDesc(
        UUID schoolId, Pageable pageable);

    Optional<WhatsAppInboxMessage> findByIdAndSchoolId(UUID id, UUID schoolId);

    long countBySchoolIdAndRoutedToIdAndReadFalse(UUID schoolId, UUID routedToId);
}
