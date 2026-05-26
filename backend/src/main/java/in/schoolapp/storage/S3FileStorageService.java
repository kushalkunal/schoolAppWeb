package in.schoolapp.storage;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.storage.config.StorageProperties;
import in.schoolapp.storage.dto.StoredFile;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * S3-compatible storage via MinIO Java SDK.
 *
 * <p><strong>Slice 9d:</strong> S3 credentials resolved per operation via
 * {@link StorageConfigResolver} — tenant DB row first (when the key starts with
 * {@code <concern>/{tenantId}/...}), JVM-global env fallback. Per-(endpoint, bucket,
 * accessKey) {@link MinioClient}s are cached so steady creds reuse the same client.
 *
 * <p>Constructor-time credential validation moved to first-use so the bean loads on a
 * tenant-only deployment (no env vars set, all tenants self-configured).
 *
 * <p>Tenant lookup: keys follow the convention {@code <concern>/{tenantId-uuid}/...} —
 * receipts, report cards, student photos/documents, school logos, migration uploads all do
 * this. The second path segment is parsed as a UUID; if it parses, the resolver is
 * consulted for that tenant, otherwise we use the global config.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "S3")
public class S3FileStorageService implements FileStorageService {

    private final StorageConfigResolver resolver;
    private final int defaultPresignMinutes;

    /** Per-creds client cache keyed by {@code endpoint|bucket|accessKey-hash}. */
    private final ConcurrentHashMap<String, ClientBundle> clientCache = new ConcurrentHashMap<>();

    public S3FileStorageService(StorageConfigResolver resolver, StorageProperties props) {
        this.resolver = resolver;
        this.defaultPresignMinutes = props.presignTtlMinutes();
    }

    @Override
    public StoredFile store(String key, byte[] bytes, String contentType) {
        ClientBundle b = bundleFor(key);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            b.client.putObject(PutObjectArgs.builder()
                .bucket(b.bucket)
                .object(key)
                .stream(in, bytes.length, -1)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build());
            String url = urlFor(b, key, Duration.ofMinutes(defaultPresignMinutes));
            log.debug("[S3-STORE] key={} bytes={} bucket={}", key, bytes.length, b.bucket);
            return new StoredFile(key, url, bytes.length, contentType);
        } catch (Exception e) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "Failed to upload to S3: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            ClientBundle b = bundleFor(key);
            b.client.removeObject(RemoveObjectArgs.builder().bucket(b.bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String presignedUrl(String key, Duration ttl) {
        return urlFor(bundleFor(key), key, ttl);
    }

    @Override
    public byte[] retrieve(String key) {
        ClientBundle b = bundleFor(key);
        try (var stream = b.client.getObject(GetObjectArgs.builder()
                .bucket(b.bucket).object(key).build())) {
            return stream.readAllBytes();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new java.util.NoSuchElementException("No stored file for key: " + key);
            }
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "Failed to read S3 object: " + key, e);
        } catch (Exception e) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "Failed to read S3 object: " + key, e);
        }
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    /** Resolves the effective S3 client + bucket for a key, caching per-creds. */
    private ClientBundle bundleFor(String key) {
        UUID tenantId = tenantIdFromKey(key);
        StorageConfigResolver.S3Creds creds = resolver.resolveS3(tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "No S3 credentials available for tenant " + tenantId
                    + " — set per-tenant config or app.storage.s3.*"));
        String cacheKey = creds.endpoint() + "|" + creds.bucket() + "|"
            + Integer.toHexString(creds.accessKeyId().hashCode());
        return clientCache.computeIfAbsent(cacheKey, k -> {
            MinioClient.Builder builder = MinioClient.builder()
                .endpoint(creds.endpoint() != null && !creds.endpoint().isBlank()
                    ? creds.endpoint() : "https://s3.amazonaws.com")
                .credentials(creds.accessKeyId(), creds.secretAccessKey());
            if (creds.region() != null && !creds.region().isBlank()) {
                builder.region(creds.region());
            }
            log.info("S3 client created for endpoint={} bucket={} region={} tenantBound={}",
                creds.endpoint(), creds.bucket(), creds.region(), creds.configId() != null);
            return new ClientBundle(builder.build(),
                creds.bucket(),
                creds.publicBaseUrl() != null ? creds.publicBaseUrl().replaceAll("/+$", "") : null);
        });
    }

    /**
     * Parses the tenant id from a storage key of the form {@code <concern>/{tenantId}/...}.
     * Returns null if the key doesn't match the convention — caller then uses the
     * JVM-global config.
     */
    static UUID tenantIdFromKey(String key) {
        if (key == null) return null;
        String[] parts = key.split("/", 4);
        if (parts.length < 2) return null;
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String urlFor(ClientBundle b, String key, Duration ttl) {
        if (b.publicBaseUrl != null) {
            return b.publicBaseUrl + "/" + key;
        }
        try {
            int seconds = (int) Math.min(ttl.getSeconds(), TimeUnit.DAYS.toSeconds(7));
            return b.client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(b.bucket)
                .object(key)
                .expiry(seconds, TimeUnit.SECONDS)
                .build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "Failed to presign URL for " + key, e);
        }
    }

    /** Cached per-creds bundle — client + bucket + optional public base URL. */
    private record ClientBundle(MinioClient client, String bucket, String publicBaseUrl) {}

    // Optional getter for the resolver — useful for tests that need to verify the cache.
    Optional<StorageConfigResolver> resolverForTesting() {
        return Optional.of(resolver);
    }
}
