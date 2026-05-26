package in.schoolapp.storage;

import in.schoolapp.storage.dto.StoredFile;

import java.time.Duration;

/**
 * Binary file storage abstraction. Consumers ({@link in.schoolapp.fee.ReceiptService} and
 * {@link in.schoolapp.academics.ReportCardService}) depend on this interface — the concrete
 * backend (local disk vs. any S3-compatible provider) is selected via {@code app.storage.provider}.
 * <p>
 * Keys are slash-separated paths like {@code "receipts/<tenantId>/<paymentId>.pdf"} — flat
 * enough to map cleanly onto both a local directory structure and S3 object keys. Implementations
 * MUST reject keys containing {@code ".."} to prevent path-traversal on LOCAL.
 */
public interface FileStorageService {

    /**
     * Persists the payload and returns a usable URL. The URL is either a presigned S3 URL or
     * the LOCAL HTTP endpoint — callers don't need to know which.
     */
    StoredFile store(String key, byte[] bytes, String contentType);

    /** Removes the file. No-op if already absent. */
    void delete(String key);

    /**
     * Retrieves the raw bytes for a previously-stored key. Preferred over round-tripping via
     * the presigned URL when the caller is in-process (e.g. MigrationProcessor re-reading an
     * uploaded image). Throws {@link java.util.NoSuchElementException} if the key is absent.
     */
    byte[] retrieve(String key);

    /**
     * Returns a time-limited URL for an already-stored file. For LOCAL this is the stable
     * {@code /files/<key>} URL ignoring the {@code ttl}. For S3-compatible backends this is a
     * freshly-signed URL honouring the TTL.
     */
    String presignedUrl(String key, Duration ttl);
}
