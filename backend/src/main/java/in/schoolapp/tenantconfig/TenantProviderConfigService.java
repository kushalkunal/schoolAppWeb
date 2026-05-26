package in.schoolapp.tenantconfig;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.tenantconfig.entity.TenantProviderConfig;
import in.schoolapp.tenantconfig.repository.TenantProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages per-tenant external-provider configurations.
 *
 * <p>API surface:
 * <ul>
 *   <li>{@link #set} — upsert a config; encrypts sensitive fields at write.</li>
 *   <li>{@link #getActive} — returns the in-process (decrypted) view for dispatcher beans.</li>
 *   <li>{@link #getActiveMasked} — returns a masked view safe for the tenant-self read.</li>
 *   <li>{@link #listForTenant} / {@link #clear} / {@link #markVerified} — admin ops.</li>
 * </ul>
 *
 * <p>Sensitive keys are the union of common credential field names across providers
 * (api_token, secret_key, etc). When the platform-admin sets a config, any value at one of
 * these keys is AES-GCM encrypted before persistence. On retrieval, in-process callers get
 * decrypted values; HTTP responses to the tenant get a masked view.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProviderConfigService {

    /**
     * Keys whose values are treated as secrets. Match is case-insensitive on the key name.
     * Add new key names here as new providers ship.
     */
    static final Set<String> SENSITIVE_KEYS = Set.of(
        "api_token", "token", "access_token",
        "secret_key", "secret", "webhook_secret",
        "password", "api_key", "key_secret",
        "client_secret", "private_key"
    );

    private static final String MASK = "********";

    private final TenantProviderConfigRepository repository;
    private final SecretCipher cipher;

    // ------------------------------------------------------------------------------------
    // Write
    // ------------------------------------------------------------------------------------

    /**
     * Upsert a tenant's provider config for one concern. If a row exists, its provider +
     * config are replaced (single active provider per concern by table-level constraint).
     *
     * @param incomingConfig PLAINTEXT config map. Sensitive fields will be encrypted before save.
     */
    @Transactional
    public TenantProviderConfig set(UUID schoolId, ProviderConcern concern, String provider,
                                    Map<String, Object> incomingConfig, String note,
                                    UUID actorId) {
        validateProvider(concern, provider);
        Map<String, Object> safe = encryptSensitive(incomingConfig);

        TenantProviderConfig row = repository.findBySchoolIdAndConcern(schoolId, concern)
            .orElseGet(() -> {
                TenantProviderConfig t = new TenantProviderConfig();
                t.setSchoolId(schoolId);
                t.setConcern(concern);
                t.setCreatedById(actorId);
                return t;
            });
        row.setProvider(provider);
        row.setConfig(safe);
        row.setActive(true);
        row.setNote(note);
        // Re-verification needed after any credential change. Caller can mark verified later.
        row.setVerifiedAt(null);
        row.setLastError(null);

        TenantProviderConfig saved = repository.save(row);
        log.info("Provider config set school={} concern={} provider={} keys={}",
            schoolId, concern, provider, redactedKeyView(incomingConfig));
        return saved;
    }

    @Transactional
    public void clear(UUID schoolId, ProviderConcern concern) {
        repository.findBySchoolIdAndConcern(schoolId, concern)
            .ifPresent(repository::delete);
    }

    @Transactional
    public void markVerified(UUID id, boolean success, String errorMessage) {
        TenantProviderConfig row = repository.findById(id)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND,
                "TenantProviderConfig", id));
        if (success) {
            row.setVerifiedAt(java.time.OffsetDateTime.now());
            row.setLastError(null);
        } else {
            row.setLastError(errorMessage);
        }
        repository.save(row);
    }

    // ------------------------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------------------------

    /**
     * For in-process dispatcher beans. Returns the row with config fields DECRYPTED.
     * Never log the returned config — secrets are in plain.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedConfig> getActive(UUID schoolId, ProviderConcern concern) {
        return repository.findBySchoolIdAndConcern(schoolId, concern)
            .filter(TenantProviderConfig::isActive)
            .map(r -> new ResolvedConfig(r.getId(), r.getSchoolId(), r.getConcern(),
                r.getProvider(), decryptAll(r.getConfig())));
    }

    /**
     * For HTTP tenant-self responses. Same shape as {@link #getActive} but secrets are
     * masked. Safe to log and return in API responses.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedConfig> getActiveMasked(UUID schoolId, ProviderConcern concern) {
        return repository.findBySchoolIdAndConcern(schoolId, concern)
            .filter(TenantProviderConfig::isActive)
            .map(r -> new ResolvedConfig(r.getId(), r.getSchoolId(), r.getConcern(),
                r.getProvider(), maskAll(r.getConfig())));
    }

    @Transactional(readOnly = true)
    public List<TenantProviderConfig> listForTenant(UUID schoolId) {
        return repository.findBySchoolId(schoolId);
    }

    /**
     * Masked, list-shaped view of every concern for a tenant — used by tenant-self endpoints
     * and the platform-admin tenant-detail screen.
     */
    @Transactional(readOnly = true)
    public List<ResolvedConfig> listForTenantMasked(UUID schoolId) {
        return repository.findBySchoolId(schoolId).stream()
            .filter(TenantProviderConfig::isActive)
            .map(r -> new ResolvedConfig(r.getId(), r.getSchoolId(), r.getConcern(),
                r.getProvider(), maskAll(r.getConfig())))
            .toList();
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private void validateProvider(ProviderConcern concern, String provider) {
        if (provider == null || provider.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "provider is required");
        }
        Set<String> allowed = ProviderType.allowedFor(concern);
        if (!allowed.contains(provider)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Provider '" + provider + "' is not valid for concern " + concern
                    + ". Allowed: " + allowed,
                Map.of("concern", concern.name(), "provider", provider, "allowed", allowed));
        }
    }

    Map<String, Object> encryptSensitive(Map<String, Object> in) {
        if (in == null) return new HashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object value = e.getValue();
            if (isSensitiveKey(e.getKey()) && value instanceof String s) {
                out.put(e.getKey(), cipher.encrypt(s));
            } else {
                out.put(e.getKey(), value);
            }
        }
        return out;
    }

    Map<String, Object> decryptAll(Map<String, Object> in) {
        if (in == null) return new HashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object value = e.getValue();
            if (value instanceof String s) {
                out.put(e.getKey(), cipher.decryptIfNeeded(s));
            } else {
                out.put(e.getKey(), value);
            }
        }
        return out;
    }

    Map<String, Object> maskAll(Map<String, Object> in) {
        if (in == null) return new HashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (isSensitiveKey(e.getKey())) {
                out.put(e.getKey(), MASK);
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return SENSITIVE_KEYS.contains(k);
    }

    /** For audit logs — show which keys were set, never any values. */
    private static Set<String> redactedKeyView(Map<String, Object> in) {
        return in == null ? Set.of() : in.keySet();
    }

    /**
     * Decrypted-or-masked view returned by service reads. Concrete record so callers can
     * destructure cleanly without depending on the entity class.
     */
    public record ResolvedConfig(
        UUID id,
        UUID schoolId,
        ProviderConcern concern,
        String provider,
        Map<String, Object> config
    ) {}
}
