package in.schoolapp.communication.template;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.communication.entity.MessageTemplate;
import in.schoolapp.communication.repository.MessageTemplateRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD for per-tenant WhatsApp / notification message templates.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/tenants/{tenantId}/message-templates} — list all templates for
 *       the school; also returns the built-in defaults so the UI can show "current effective
 *       body" for each template key.</li>
 *   <li>{@code PUT  /api/v1/tenants/{tenantId}/message-templates/{key}} — upsert; creates or
 *       replaces the override for the given key.</li>
 *   <li>{@code DELETE /api/v1/tenants/{tenantId}/message-templates/{key}} — removes the
 *       override so the built-in default is used again.</li>
 * </ul>
 *
 * Template keys match {@link in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType}
 * constant names. The controller validates only that the key is non-blank; the service layer
 * in {@link MessageTemplateService} recognises any key stored in the DB.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/message-templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateRepository repository;
    private final MessageTemplateService templateService;
    private final AuditLogger auditLogger;

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record TemplateResponse(
        UUID id,
        String templateKey,
        String bodyTemplate,
        String defaultBody
    ) {}

    public record UpsertRequest(
        @NotBlank @Size(max = 60) String templateKey,
        @NotBlank String bodyTemplate
    ) {}

    // ── Endpoints ────────────────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<List<TemplateResponse>> list(@PathVariable UUID tenantId) {
        List<MessageTemplate> stored = repository.findBySchoolIdOrderByTemplateKeyAsc(tenantId);
        // Build a map of stored templates to decorate with defaults
        Map<String, MessageTemplate> byKey = new java.util.HashMap<>();
        stored.forEach(t -> byKey.put(t.getTemplateKey(), t));

        // Surface all well-known keys with their default text; overlay stored overrides
        List<TemplateResponse> result = templateService.knownKeys().stream()
            .map(key -> {
                MessageTemplate mt = byKey.get(key);
                return new TemplateResponse(
                    mt != null ? mt.getId() : null,
                    key,
                    mt != null ? mt.getBodyTemplate() : null,
                    templateService.defaultBody(key)
                );
            })
            .toList();
        return ApiResponse.success(result);
    }

    @PutMapping("/{key}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ApiResponse<TemplateResponse> upsert(
        @PathVariable UUID tenantId,
        @PathVariable String key,
        @Valid @RequestBody UpsertRequest req
    ) {
        MessageTemplate mt = repository.findBySchoolIdAndTemplateKey(tenantId, key)
            .orElseGet(() -> {
                MessageTemplate n = new MessageTemplate();
                n.setSchoolId(tenantId);
                n.setTemplateKey(key);
                return n;
            });
        mt.setBodyTemplate(req.bodyTemplate().trim());
        mt = repository.save(mt);
        auditLogger.logUpdate(tenantId, "MessageTemplate", mt.getId(),
            Map.of(), Map.of("key", key, "bodyTemplate", (Object) mt.getBodyTemplate()));
        return ApiResponse.success(new TemplateResponse(
            mt.getId(), mt.getTemplateKey(), mt.getBodyTemplate(),
            templateService.defaultBody(key)));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @Transactional
    public ApiResponse<Void> reset(@PathVariable UUID tenantId, @PathVariable String key) {
        repository.findBySchoolIdAndTemplateKey(tenantId, key).ifPresent(mt -> {
            repository.delete(mt);
            auditLogger.logDelete(tenantId, "MessageTemplate", mt.getId(), Map.of("key", key));
        });
        return ApiResponse.success(null);
    }
}
