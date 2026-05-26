package in.schoolapp.communication;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.communication.dto.InboxMessageResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Inbox views over {@code whatsapp_inbox_messages}:
 * <ul>
 *   <li>{@code /mine} — current teacher's routed messages (default view after login)</li>
 *   <li>{@code /unrouted} — principal triage queue for unknown-phone messages</li>
 *   <li>{@code /all} — principal read-only overview</li>
 *   <li>POST {@code /{id}/read} / {@code /resolve} / {@code /reassign}</li>
 * </ul>
 * The webhook keeps writing into the same table via {@link WhatsAppInboxRoutingService}; this
 * controller is read-and-triage only.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inbox")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.WHATSAPP_INBOX)
public class InboxController {

    private final InboxService inboxService;

    @GetMapping("/mine")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<Page<InboxMessageResponse>> mine(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        UUID me = InboxService.currentStaffId();
        Page<InboxMessageResponse> result = inboxService.listForTeacher(tenantId, me, page, size);
        return ApiResponse.success(result,
            new ApiResponse.Meta(result.getTotalElements(), result.getNumber(), result.getSize(), null));
    }

    @GetMapping("/mine/unread-count")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<Map<String, Long>> unreadCount(@PathVariable UUID tenantId) {
        UUID me = InboxService.currentStaffId();
        return ApiResponse.success(Map.of("unread", inboxService.unreadCount(tenantId, me)));
    }

    @GetMapping("/unrouted")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Page<InboxMessageResponse>> unrouted(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Page<InboxMessageResponse> result = inboxService.listUnrouted(tenantId, page, size);
        return ApiResponse.success(result,
            new ApiResponse.Meta(result.getTotalElements(), result.getNumber(), result.getSize(), null));
    }

    @GetMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Page<InboxMessageResponse>> all(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Page<InboxMessageResponse> result = inboxService.listAllForTenant(tenantId, page, size);
        return ApiResponse.success(result,
            new ApiResponse.Meta(result.getTotalElements(), result.getNumber(), result.getSize(), null));
    }

    @PostMapping("/{messageId}/read")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<InboxMessageResponse> markRead(
        @PathVariable UUID tenantId, @PathVariable UUID messageId
    ) {
        return ApiResponse.success(inboxService.markRead(tenantId, messageId));
    }

    @PostMapping("/{messageId}/resolve")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<InboxMessageResponse> markResolved(
        @PathVariable UUID tenantId, @PathVariable UUID messageId
    ) {
        return ApiResponse.success(inboxService.markResolved(tenantId, messageId));
    }

    @PostMapping("/{messageId}/reassign")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<InboxMessageResponse> reassign(
        @PathVariable UUID tenantId,
        @PathVariable UUID messageId,
        @RequestParam UUID toTeacherId
    ) {
        return ApiResponse.success(inboxService.reassign(tenantId, messageId, toTeacherId));
    }
}
