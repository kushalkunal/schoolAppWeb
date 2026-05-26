package in.schoolapp.communication.parent;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.communication.parent.entity.ParentMessage;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/parent-messages")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.UNIFIED_INBOX)
public class ParentInboxController {

    private final ParentMessageRepository repo;

    @GetMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Page<ParentMessage>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        return ApiResponse.success(repo.findBySchoolIdOrderBySentAtDesc(tenantId, PageRequest.of(page, size)));
    }

    @GetMapping("/by-student/{studentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<ParentMessage>> byStudent(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(repo.findByStudentIdOrderBySentAtDesc(studentId));
    }
}
