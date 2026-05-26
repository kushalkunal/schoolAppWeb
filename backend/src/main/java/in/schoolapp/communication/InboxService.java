package in.schoolapp.communication;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.common.TenantContext;
import in.schoolapp.communication.dto.InboxMessageResponse;
import in.schoolapp.communication.entity.WhatsAppInboxMessage;
import in.schoolapp.communication.repository.WhatsAppInboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read / mark-read / mark-resolved surface for the teacher + principal inbox. Writes go
 * through {@link WhatsAppInboxRoutingService} (webhook path only); this service never creates
 * rows.
 */
@Service
@RequiredArgsConstructor
public class InboxService {

    private static final int MAX_PAGE_SIZE = 200;

    private final WhatsAppInboxMessageRepository inboxRepository;

    @Transactional(readOnly = true)
    public Page<InboxMessageResponse> listForTeacher(UUID tenantId, UUID teacherId, int page, int size) {
        int pageSize = clampSize(size);
        return inboxRepository
            .findBySchoolIdAndRoutedToIdOrderByReceivedAtDesc(
                tenantId, teacherId, PageRequest.of(Math.max(0, page), pageSize))
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<InboxMessageResponse> listAllForTenant(UUID tenantId, int page, int size) {
        int pageSize = clampSize(size);
        return inboxRepository
            .findBySchoolIdOrderByReceivedAtDesc(tenantId, PageRequest.of(Math.max(0, page), pageSize))
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<InboxMessageResponse> listUnrouted(UUID tenantId, int page, int size) {
        int pageSize = clampSize(size);
        return inboxRepository
            .findBySchoolIdAndRoutedToIdIsNullOrderByReceivedAtDesc(
                tenantId, PageRequest.of(Math.max(0, page), pageSize))
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID tenantId, UUID teacherId) {
        return inboxRepository.countBySchoolIdAndRoutedToIdAndReadFalse(tenantId, teacherId);
    }

    @Transactional
    public InboxMessageResponse markRead(UUID tenantId, UUID messageId) {
        WhatsAppInboxMessage m = requireMessage(tenantId, messageId);
        if (!m.isRead()) {
            m.setRead(true);
            inboxRepository.save(m);
        }
        return toDto(m);
    }

    @Transactional
    public InboxMessageResponse markResolved(UUID tenantId, UUID messageId) {
        WhatsAppInboxMessage m = requireMessage(tenantId, messageId);
        m.setResolved(true);
        m.setRead(true);  // resolving implies reading
        inboxRepository.save(m);
        return toDto(m);
    }

    /**
     * Principal re-routing: assign a (previously unrouted) message to a specific teacher.
     * Caller-level authorisation is enforced at the controller; this service just writes.
     */
    @Transactional
    public InboxMessageResponse reassign(UUID tenantId, UUID messageId, UUID newTeacherId) {
        WhatsAppInboxMessage m = requireMessage(tenantId, messageId);
        m.setRoutedToId(newTeacherId);
        inboxRepository.save(m);
        return toDto(m);
    }

    private WhatsAppInboxMessage requireMessage(UUID tenantId, UUID messageId) {
        return inboxRepository.findByIdAndSchoolId(messageId, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "InboxMessage", messageId));
    }

    private InboxMessageResponse toDto(WhatsAppInboxMessage m) {
        return InboxMessageResponse.from(m, PhoneNormalizer.mask(m.getFromPhone()));
    }

    private static int clampSize(int size) {
        if (size <= 0) return 50;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Helper for the controller: the current JWT subject (staff id). */
    public static UUID currentStaffId() {
        UUID id = TenantContext.getStaffId();
        if (id == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        return id;
    }
}
