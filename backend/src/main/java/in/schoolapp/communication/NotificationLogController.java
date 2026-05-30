package in.schoolapp.communication;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.communication.entity.NotificationLog;
import in.schoolapp.communication.entity.NotificationStatus;
import in.schoolapp.communication.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Exposes the notification audit trail and a re-send action to front-end staff.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/tenants/{tenantId}/notification-logs} — paginated list with optional
 *       {@code status} and {@code eventType} filters.</li>
 *   <li>{@code POST /api/v1/tenants/{tenantId}/notification-logs/{id}/resend} — re-dispatches the
 *       original message body to the same phone number; creates a new log row (does NOT mutate the
 *       original row so the audit trail stays intact).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/notification-logs")
@RequiredArgsConstructor
@PreAuthorize(AppRoles.OWNER_OR_ADMIN)
public class NotificationLogController {

    private final NotificationLogRepository repository;
    private final WhatsAppNotifier notifier;

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record NotificationLogResponse(
        UUID id,
        String eventType,
        String channel,
        String recipientPhone,
        String recipientName,
        UUID studentId,
        String messageBody,
        String status,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime readAt
    ) {
        static NotificationLogResponse from(NotificationLog r) {
            return new NotificationLogResponse(
                r.getId(), r.getEventType(), r.getChannel(),
                r.getRecipientPhone(), r.getRecipientName(), r.getStudentId(),
                r.getMessageBody(), r.getStatus().name(), r.getErrorMessage(),
                r.getCreatedAt(), r.getSentAt(), r.getDeliveredAt(), r.getReadAt());
        }
    }

    public record PagedResponse<T>(List<T> items, long total, int page, int size) {}

    // ── Endpoints ────────────────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<PagedResponse<NotificationLogResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String eventType
    ) {
        int clampedSize = Math.min(size, 100);
        PageRequest pr = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationLog> result;

        if (status != null && eventType != null) {
            NotificationStatus ns = parseStatus(status);
            result = repository.findBySchoolIdAndStatusAndEventType(tenantId, ns, eventType.toUpperCase(), pr);
        } else if (status != null) {
            result = repository.findBySchoolIdAndStatus(tenantId, parseStatus(status), pr);
        } else if (eventType != null) {
            result = repository.findBySchoolIdAndEventType(tenantId, eventType.toUpperCase(), pr);
        } else {
            result = repository.findBySchoolId(tenantId, pr);
        }

        List<NotificationLogResponse> items = result.getContent().stream()
            .map(NotificationLogResponse::from).toList();
        return ApiResponse.success(new PagedResponse<>(items, result.getTotalElements(), page, clampedSize));
    }

    @PostMapping("/{id}/resend")
    @Transactional(readOnly = true)
    public ApiResponse<Void> resend(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        NotificationLog original = repository.findById(id)
            .filter(l -> l.getSchoolId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log entry not found"));

        if (original.getRecipientPhone() == null || original.getMessageBody() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Cannot resend — missing phone or message body");
        }

        // Resolve message type; fall back to CIRCULAR for legacy/unknown types
        WhatsAppMessage.MessageType type;
        try {
            type = WhatsAppMessage.MessageType.valueOf(original.getEventType());
        } catch (IllegalArgumentException e) {
            type = WhatsAppMessage.MessageType.CIRCULAR;
        }

        WhatsAppMessage.Audit audit = new WhatsAppMessage.Audit(
            tenantId, original.getStudentId(), original.getParentId(),
            original.getRecipientName());

        notifier.send(WhatsAppMessage.text(
            original.getRecipientPhone(), original.getMessageBody(), type, audit));

        return ApiResponse.success(null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static NotificationStatus parseStatus(String s) {
        try {
            return NotificationStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + s);
        }
    }
}
