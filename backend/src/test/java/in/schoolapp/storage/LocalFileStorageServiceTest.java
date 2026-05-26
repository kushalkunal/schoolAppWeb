package in.schoolapp.storage;

import in.schoolapp.common.AppException;
import in.schoolapp.storage.config.StorageProperties;
import in.schoolapp.storage.config.StorageProperties.LocalConfig;
import in.schoolapp.storage.config.StorageProperties.Provider;
import in.schoolapp.storage.dto.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private StorageProperties propsWith(Path baseDir) {
        return new StorageProperties(
            Provider.LOCAL, 60,
            new LocalConfig(baseDir.toString(), "http://localhost:8080/files"),
            null
        );
    }

    @Test
    void store_writesBytesAndReturnsHttpUrl(@TempDir Path base) {
        LocalFileStorageService svc = new LocalFileStorageService(propsWith(base));
        StoredFile result = svc.store("receipts/t1/p1.pdf",
            "hello".getBytes(), "application/pdf");

        assertThat(result.key()).isEqualTo("receipts/t1/p1.pdf");
        assertThat(result.url()).isEqualTo("http://localhost:8080/files/receipts/t1/p1.pdf");
        assertThat(result.sizeBytes()).isEqualTo(5L);
        assertThat(Files.exists(base.resolve("receipts/t1/p1.pdf"))).isTrue();
    }

    @Test
    void store_rejectsPathTraversal(@TempDir Path base) {
        LocalFileStorageService svc = new LocalFileStorageService(propsWith(base));
        assertThatThrownBy(() ->
            svc.store("../escape.pdf", new byte[]{}, "application/pdf"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void store_rejectsAbsoluteKey(@TempDir Path base) {
        LocalFileStorageService svc = new LocalFileStorageService(propsWith(base));
        assertThatThrownBy(() ->
            svc.store("/etc/passwd", new byte[]{}, "application/pdf"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void delete_removesFile(@TempDir Path base) {
        LocalFileStorageService svc = new LocalFileStorageService(propsWith(base));
        svc.store("a/b.pdf", "x".getBytes(), "application/pdf");
        assertThat(Files.exists(base.resolve("a/b.pdf"))).isTrue();
        svc.delete("a/b.pdf");
        assertThat(Files.exists(base.resolve("a/b.pdf"))).isFalse();
    }

    @Test
    void presignedUrl_returnsStableUrlIgnoringTtl(@TempDir Path base) {
        LocalFileStorageService svc = new LocalFileStorageService(propsWith(base));
        String url = svc.presignedUrl("a/b.pdf", Duration.ofMinutes(5));
        assertThat(url).isEqualTo("http://localhost:8080/files/a/b.pdf");
    }

    @Test
    void resolvesKey_returnsPathInsideBaseDir(@TempDir Path base) {
        LocalFileStorageService svc = new LocalFileStorageService(propsWith(base));
        Path resolved = svc.resolve("nested/file.pdf");
        assertThat(resolved.startsWith(base.toAbsolutePath())).isTrue();
    }

    @Test
    void store_handlesTrailingSlashInPublicBaseUrl(@TempDir Path base) {
        StorageProperties props = new StorageProperties(
            Provider.LOCAL, 60,
            new LocalConfig(base.toString(), "http://localhost:8080/files///"),
            null
        );
        LocalFileStorageService svc = new LocalFileStorageService(props);
        StoredFile result = svc.store("x.pdf", new byte[]{1}, "application/octet-stream");
        assertThat(result.url()).isEqualTo("http://localhost:8080/files/x.pdf");
    }
}
