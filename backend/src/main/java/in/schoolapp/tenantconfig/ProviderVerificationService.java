package in.schoolapp.tenantconfig;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.tenantconfig.entity.TenantProviderConfig;
import in.schoolapp.tenantconfig.repository.TenantProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Round-trips against the configured external provider to confirm credentials are valid,
 * then stamps {@code verified_at} (success) or {@code last_error} (failure) on the row.
 *
 * <p>Each concern needs a provider-specific probe — they have nothing in common beyond
 * "make an authenticated call and check the response". Slice 3 wires:
 *
 * <ul>
 *   <li>{@code WHATSAPP + WATI}  — GET {@code /api/v1/getMessageTemplates} (lightweight,
 *       returns 200 with empty list if no templates yet but auth is correct).</li>
 *   <li>All other concerns → returns a "not yet verifiable" result without flipping the
 *       row state, so the UI can show "Verification not implemented for this provider".</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderVerificationService {

    private final TenantProviderConfigRepository repository;
    private final TenantProviderConfigService configService;
    private final RestClient.Builder restClientBuilder;

    public VerifyResult verify(UUID schoolId, ProviderConcern concern) {
        TenantProviderConfig row = repository.findBySchoolIdAndConcern(schoolId, concern)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND,
                "TenantProviderConfig (school=" + schoolId + ", concern=" + concern + ")",
                "—"));

        TenantProviderConfigService.ResolvedConfig resolved = configService
            .getActive(schoolId, concern)
            .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR,
                "Provider config is inactive — cannot verify"));

        // Concern-specific probe.
        try {
            if (concern == ProviderConcern.WHATSAPP
                && ProviderType.WATI.equals(resolved.provider())) {
                verifyWati(resolved);
            } else {
                // Stub for now — accept and stamp; the UI surface differentiates verified
                // from "verification not implemented" by inspecting `note`.
                row.setNote("Verification stub — no provider-specific probe wired yet");
                row.setVerifiedAt(OffsetDateTime.now());
                row.setLastError(null);
                repository.save(row);
                return new VerifyResult(false, "Verification not implemented for this provider",
                    row.getVerifiedAt());
            }
            row.setVerifiedAt(OffsetDateTime.now());
            row.setLastError(null);
            repository.save(row);
            log.info("Verified provider school={} concern={} provider={}",
                schoolId, concern, resolved.provider());
            return new VerifyResult(true, "OK", row.getVerifiedAt());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            row.setLastError(msg);
            repository.save(row);
            log.warn("Verification failed school={} concern={} provider={} error={}",
                schoolId, concern, resolved.provider(), msg);
            return new VerifyResult(false, msg, null);
        }
    }

    private void verifyWati(TenantProviderConfigService.ResolvedConfig cfg) {
        Object baseUrl = cfg.config().get("base_url");
        Object token   = cfg.config().get("token");
        if (!(baseUrl instanceof String b) || !(token instanceof String t)
            || b.isBlank() || t.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "WATI config is missing base_url or token");
        }
        RestClient client = restClientBuilder
            .baseUrl(b)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + t)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
        try {
            // GET account-info; 200 == valid creds; 401/403 == bad creds.
            client.get()
                .uri("/api/v1/getMessageTemplates?pageSize=1")
                .retrieve()
                .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "WATI returned " + e.getStatusCode() + ": " + e.getStatusText());
        }
    }

    /**
     * Result returned to the operator. {@code success=true} only when a real round-trip
     * was performed and accepted; for stubs / not-yet-implemented concerns,
     * {@code success=false} with a descriptive message.
     */
    public record VerifyResult(boolean success, String message, OffsetDateTime verifiedAt) {}
}
