package in.schoolapp.branding;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Two surfaces:
 *
 * <ul>
 *   <li>{@code GET /api/v1/public/schools/{schoolId}/branding} — public; the frontend
 *       deploy fetches this on mount to apply runtime overrides without a rebuild.
 *       Allowlisted in SecurityConfig.</li>
 *   <li>{@code PUT /api/v1/tenants/{tenantId}/branding} — authenticated; OWNER_OR_ADMIN
 *       updates the school's logo / colours / GSTIN / contact strip.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;

    @GetMapping("/api/v1/public/schools/{schoolId}/branding")
    public ApiResponse<BrandingResponse> publicBranding(@PathVariable UUID schoolId) {
        return ApiResponse.success(brandingService.get(schoolId));
    }

    @PutMapping("/api/v1/tenants/{tenantId}/branding")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<BrandingResponse> update(
        @PathVariable UUID tenantId,
        @Valid @RequestBody BrandingResponse patch
    ) {
        return ApiResponse.success(brandingService.update(tenantId, patch));
    }
}
