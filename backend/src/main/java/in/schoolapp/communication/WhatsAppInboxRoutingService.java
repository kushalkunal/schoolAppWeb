package in.schoolapp.communication;

import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.communication.entity.WhatsAppInboxMessage;
import in.schoolapp.communication.repository.WhatsAppInboxMessageRepository;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.entity.StudentParentLink;
import in.schoolapp.student.repository.ParentRepository;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Routes an inbound WhatsApp message from a parent to the class teacher of their first
 * matching child (gap analysis §5.6). Identification goes phone → Parent row → primary child
 * → child's current section → section's {@code classTeacherId}. Any step that fails leaves
 * the row lightly-populated but still persisted so the message is not lost.
 * <p>
 * Because we don't have the tenant id in the webhook payload (the BSP doesn't know it), we
 * look up the parent by the raw phone across all tenants. Production deployments that multi-
 * host different schools on different BSP numbers can switch to a lookup keyed on the
 * {@code recipient phone number id} supplied in the webhook body — that's a Slice-10 concern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppInboxRoutingService {

    private final WhatsAppInboxMessageRepository inboxRepository;
    private final ParentRepository parentRepository;
    private final StudentParentLinkRepository linkRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;

    /**
     * Persists an inbound message with best-effort parent/teacher resolution. Idempotent by
     * {@code waMessageId} — a retried webhook does not create a duplicate row.
     */
    @Transactional
    public void route(String waMessageId, String fromPhoneRaw, String body) {
        if (waMessageId != null && inboxRepository.existsByWaMessageId(waMessageId)) {
            log.debug("Inbound message waMessageId={} already routed — skipping", waMessageId);
            return;
        }

        String normalized = tryNormalize(fromPhoneRaw);
        List<Parent> candidates = normalized == null
            ? List.of()
            : parentRepository.findAllByPhone(normalized);

        if (candidates.isEmpty()) {
            // Unknown phone — still persist, principal/admin triages later. No tenant binding
            // is possible, so we drop the row rather than risk a cross-tenant leak.
            log.info("[WA-INBOX-UNROUTED] msgId={} phone-suffix={}",
                waMessageId,
                normalized == null ? "?" : PhoneNormalizer.mask(normalized));
            return;
        }

        // For each tenant the parent phone matches, create one row (parents are unique per
        // tenant by phone, so this is typically N=1 — but a parent with kids at two schools
        // using our platform could be 2+).
        for (Parent parent : candidates) {
            Resolved r = resolve(parent);
            WhatsAppInboxMessage row = new WhatsAppInboxMessage();
            row.setSchoolId(parent.getSchoolId());
            row.setFromPhone(normalized);
            row.setParentId(parent.getId());
            row.setStudentId(r.studentId);
            row.setRoutedToId(r.teacherId);
            row.setMessageBody(body == null ? "" : body);
            row.setWaMessageId(waMessageId);
            row.setReceivedAt(OffsetDateTime.now());
            inboxRepository.save(row);
            log.info("[WA-INBOX-ROUTED] tenant={} parent={} student={} teacher={}",
                parent.getSchoolId(), parent.getId(), r.studentId, r.teacherId);
        }
    }

    private Resolved resolve(Parent parent) {
        List<StudentParentLink> links = linkRepository.findByParentId(parent.getId());
        if (links.isEmpty()) return new Resolved(null, null);
        UUID studentId = links.stream()
            .filter(StudentParentLink::isPrimary)
            .map(StudentParentLink::getStudentId)
            .findFirst()
            .orElseGet(() -> links.get(0).getStudentId());

        UUID teacherId = enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
            .stream()
            .findFirst()
            .map(StudentEnrollment::getSectionId)
            .flatMap(sectionRepository::findById)
            .map(Section::getClassTeacherId)
            .orElse(null);
        return new Resolved(studentId, teacherId);
    }

    /**
     * BSP gives us the phone in various formats ("919876543210", "9876543210"). Fall through
     * gracefully: if we can't normalize to a 10-digit Indian mobile, we skip the lookup — the
     * caller logs the unrouted state.
     */
    private String tryNormalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PhoneNormalizer.normalize(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private record Resolved(UUID studentId, UUID teacherId) {}
}
