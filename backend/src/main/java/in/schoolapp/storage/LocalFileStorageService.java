package in.schoolapp.storage;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.storage.config.StorageProperties;
import in.schoolapp.storage.dto.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Writes files under {@code app.storage.local.base-dir} and serves them via {@link FileController}
 * at {@code app.storage.local.public-base-url}/<key>. Default provider — safe for dev and
 * single-node deploys; not recommended for multi-node since each node has a separate disk.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalFileStorageService(StorageProperties props) {
        if (props.local() == null) {
            throw new IllegalStateException(
                "app.storage.provider=LOCAL but app.storage.local.* is not configured");
        }
        // Normalise so the path-traversal check below (target.startsWith(baseDir)) doesn't
        // mis-fire when the configured baseDir contains "./" segments — e.g. default
        // "./storage" was previously stored as "...\backend\.\storage" while resolved targets
        // came back as "...\backend\storage", causing all writes to be rejected.
        this.baseDir = Path.of(props.local().baseDir()).toAbsolutePath().normalize();
        this.publicBaseUrl = props.local().publicBaseUrl().replaceAll("/+$", "");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage base dir: " + baseDir, e);
        }
        log.info("LocalFileStorage initialised baseDir={} publicBaseUrl={}", baseDir, publicBaseUrl);
    }

    @Override
    public StoredFile store(String key, byte[] bytes, String contentType) {
        validateKey(key);
        try {
            Path target = baseDir.resolve(key).normalize();
            if (!target.startsWith(baseDir)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid storage key (traversal attempt)");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            String url = publicBaseUrl + "/" + key;
            return new StoredFile(key, url, bytes.length, contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write file to local storage: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        try {
            Path target = baseDir.resolve(key).normalize();
            if (!target.startsWith(baseDir)) return;
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete local file {}: {}", key, e.getMessage());
        }
    }

    @Override
    public byte[] retrieve(String key) {
        validateKey(key);
        Path target = baseDir.resolve(key).normalize();
        if (!target.startsWith(baseDir) || !Files.exists(target)) {
            throw new java.util.NoSuchElementException("No stored file for key: " + key);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read local file: " + key, e);
        }
    }

    /** LOCAL storage has no signing — just returns the stable URL; TTL is ignored. */
    @Override
    public String presignedUrl(String key, Duration ttl) {
        validateKey(key);
        return publicBaseUrl + "/" + key;
    }

    /** Exposed package-private so {@link FileController} can resolve requested paths. */
    Path resolve(String key) {
        validateKey(key);
        Path target = baseDir.resolve(key).normalize();
        if (!target.startsWith(baseDir)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid storage key");
        }
        return target;
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Storage key is required");
        }
        if (key.contains("..") || key.startsWith("/") || key.contains("\\")) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Storage key must be a relative forward-slash path without '..' segments");
        }
    }
}
