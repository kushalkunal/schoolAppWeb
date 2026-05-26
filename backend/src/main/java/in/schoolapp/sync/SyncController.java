package in.schoolapp.sync;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.sync.dto.SyncPullResponse;
import in.schoolapp.sync.dto.SyncPushRequest;
import in.schoolapp.sync.dto.SyncPushResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mobile-sync endpoints for the teacher app (Slice 11). Two routes:
 * <ul>
 *   <li>{@code GET /sync/pull?since=...} — delta snapshot of students + current sections +
 *       academic year. Client stores {@code serverTime} from the response and passes it as
 *       the next {@code since}.</li>
 *   <li>{@code POST /sync/push} — batch of attendance entries queued while offline. Each
 *       entry carries a client-generated {@code localId} so the response can be correlated
 *       against the phone's SQLite rows without re-scanning by natural key.</li>
 * </ul>
 * Both endpoints are restricted to teachers/admins — accountants and viewers have no mobile
 * workflow (parents use WhatsApp only).
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/sync")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.MOBILE_SYNC)
public class SyncController {

    private final SyncPullService pullService;
    private final SyncPushService pushService;

    @GetMapping("/pull")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<SyncPullResponse> pull(
        @PathVariable UUID tenantId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since
    ) {
        return ApiResponse.success(pullService.pull(tenantId, since));
    }

    @PostMapping("/push")
    @PreAuthorize(AppRoles.ATTENDANCE_WRITER)
    public ResponseEntity<ApiResponse<SyncPushResponse>> push(
        @PathVariable UUID tenantId,
        @Valid @RequestBody SyncPushRequest request
    ) {
        SyncPushResponse response = pushService.apply(tenantId, request);
        // 207-style semantics aren't standard; use 200 with per-entry results. Accepted counts
        // are on the body.
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(response));
    }
}
