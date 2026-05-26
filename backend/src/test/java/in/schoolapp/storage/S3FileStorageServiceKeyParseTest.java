package in.schoolapp.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@code <concern>/{tenantId}/...} key-path parsing that drives per-tenant S3
 * routing. The MinIO HTTP side isn't exercised here — that's covered by the integration
 * smoke test against a real S3 / MinIO endpoint.
 */
class S3FileStorageServiceKeyParseTest {

    @Test
    void parsesTenantId_fromStandardConcernPath() {
        UUID tenant = UUID.randomUUID();
        String key = "receipts/" + tenant + "/abc123.pdf";
        assertThat(S3FileStorageService.tenantIdFromKey(key)).isEqualTo(tenant);
    }

    @Test
    void parsesTenantId_fromStudentDocsPath() {
        UUID tenant = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        String key = "student-docs/" + tenant + "/" + student + "/admission.pdf";
        assertThat(S3FileStorageServiceKeyParseTest.this).isNotNull();
        assertThat(S3FileStorageService.tenantIdFromKey(key)).isEqualTo(tenant);
    }

    @Test
    void returnsNull_whenSecondSegmentIsNotUuid() {
        assertThat(S3FileStorageService.tenantIdFromKey("logos/not-a-uuid/file.png")).isNull();
    }

    @Test
    void returnsNull_forSingleSegmentKey() {
        assertThat(S3FileStorageService.tenantIdFromKey("standalone.txt")).isNull();
    }

    @Test
    void returnsNull_forNullKey() {
        assertThat(S3FileStorageService.tenantIdFromKey(null)).isNull();
    }
}
