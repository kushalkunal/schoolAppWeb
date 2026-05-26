package in.schoolapp.storage.dto;

/**
 * Handle returned by {@link in.schoolapp.storage.FileStorageService#store}.
 * <ul>
 *   <li>{@code key}  — stable identifier; same value that was passed to {@code store()}. Use
 *       this for {@link in.schoolapp.storage.FileStorageService#delete} or to request a fresh
 *       presigned URL later.</li>
 *   <li>{@code url}  — directly usable URL. For LOCAL this is the HTTP path served by
 *       {@code FileController}; for S3 this is a presigned URL (or public URL if bucket is
 *       public) valid for the default expiry window.</li>
 * </ul>
 */
public record StoredFile(
    String key,
    String url,
    long sizeBytes,
    String contentType
) {}
