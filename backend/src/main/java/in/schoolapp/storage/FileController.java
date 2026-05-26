package in.schoolapp.storage;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves files that {@link LocalFileStorageService} wrote to disk. Only mounted when
 * {@code app.storage.provider=LOCAL} — when S3 is active, files are served directly by the
 * S3 endpoint (via presigned URLs) and this controller is absent.
 * <p>
 * Unauthenticated by design: receipt + report-card URLs are embedded in WhatsApp messages
 * and parents need to open them without a JWT. The path segment itself contains tenant +
 * entity UUIDs which are unguessable — acceptable for Slice 8 dev. Slice 9 can tighten this
 * with short-lived signed path tokens if needed.
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "LOCAL", matchIfMissing = true)
public class FileController {

    private final LocalFileStorageService storage;

    @GetMapping("/{*key}")
    public ResponseEntity<FileSystemResource> serve(@PathVariable String key) {
        String cleaned = key.startsWith("/") ? key.substring(1) : key;
        Path path = storage.resolve(cleaned);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "File", cleaned);
        }
        String contentType = Optional.ofNullable(probeContentType(path))
            .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + path.getFileName() + "\"")
            .body(new FileSystemResource(path));
    }

    private static String probeContentType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException e) {
            return null;
        }
    }
}
