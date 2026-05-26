package in.schoolapp.storage;

import in.schoolapp.storage.config.StorageProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves effective S3 storage credentials for a tenant.
 *
 * <p><strong>Slice 8d:</strong> infrastructure landed; the {@link S3FileStorageService}
 * refactor to actually consult this resolver is deferred. Most multi-tenant SaaS deployments
 * share a single bucket and namespace by key path (e.g.
 * {@code receipts/{tenantId}/{paymentId}.pdf}), so per-tenant buckets are an enterprise edge
 * case rather than the common path. When the refactor lands, the storage service will need to
 * parse the tenantId from the key prefix to call {@link #resolve} — current key conventions
 * already place tenantId as the second path segment for every concern.
 *
 * <p>Resolution order (same pattern as WhatsApp / Payment / Email):
 * <ol>
 *   <li>{@code tenant_provider_configs(schoolId, STORAGE, S3)}.</li>
 *   <li>JVM-global {@link StorageProperties#s3()} env vars.</li>
 *   <li>Empty — caller should fall back to LOCAL or raise.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final StorageProperties globalProps;

    public Optional<S3Creds> resolveS3(UUID schoolId) {
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.STORAGE);
            if (perTenant.isPresent() && ProviderType.S3.equals(perTenant.get().provider())) {
                Object endpoint = perTenant.get().config().get("endpoint");
                Object region   = perTenant.get().config().get("region");
                Object bucket   = perTenant.get().config().get("bucket");
                Object accessKey = perTenant.get().config().get("access_key_id");
                Object secretKey = perTenant.get().config().get("secret_access_key");
                Object publicBase = perTenant.get().config().get("public_base_url");
                if (endpoint instanceof String e && bucket instanceof String b
                    && accessKey instanceof String ak && secretKey instanceof String sk) {
                    return Optional.of(new S3Creds(
                        e,
                        region instanceof String r ? r : "auto",
                        b, ak, sk,
                        publicBase instanceof String pb ? pb : null,
                        perTenant.get().id()
                    ));
                }
                log.warn("Tenant {} has S3 config row but required fields missing", schoolId);
            }
        }
        if (globalProps.s3() != null && globalProps.s3().bucket() != null
            && globalProps.s3().accessKeyId() != null
            && globalProps.s3().secretAccessKey() != null) {
            return Optional.of(new S3Creds(
                globalProps.s3().endpoint(),
                globalProps.s3().region(),
                globalProps.s3().bucket(),
                globalProps.s3().accessKeyId(),
                globalProps.s3().secretAccessKey(),
                globalProps.s3().publicBaseUrl(),
                null
            ));
        }
        return Optional.empty();
    }

    public record S3Creds(
        String endpoint,
        String region,
        String bucket,
        String accessKeyId,
        String secretAccessKey,
        String publicBaseUrl,
        UUID configId
    ) {}
}
