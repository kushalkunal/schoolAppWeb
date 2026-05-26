package in.schoolapp.communication.repository;

import in.schoolapp.communication.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /** Webhook status-update lookup: find the row we created when we dispatched this message. */
    Optional<NotificationLog> findByWaMessageId(String waMessageId);

    /**
     * Most recent outbound thread for a phone number. Used by inbox routing to look up the
     * "last template / operator we sent to this parent" — lets the teacher see the context of
     * an incoming reply.
     */
    List<NotificationLog> findTop5BySchoolIdAndRecipientPhoneOrderByCreatedAtDesc(
        UUID schoolId, String recipientPhone);
}
