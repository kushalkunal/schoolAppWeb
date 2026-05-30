package in.schoolapp.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** VIEWER must not see raw contact PII (audit #3); every other role sees full values. */
class PiiMaskingTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void viewerGetsMaskedPhoneAndEmail() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "VIEWER");
        assertThat(PiiMasking.phone("9876543210")).isEqualTo("98765****10");
        assertThat(PiiMasking.email("rajesh@example.com")).isEqualTo("r****@example.com");
    }

    @Test
    void nonViewerSeesRawValues() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        assertThat(PiiMasking.phone("9876543210")).isEqualTo("9876543210");
        assertThat(PiiMasking.email("rajesh@example.com")).isEqualTo("rajesh@example.com");
    }

    @Test
    void nullsAreSafe() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "VIEWER");
        assertThat(PiiMasking.email(null)).isNull();
        assertThat(PiiMasking.phone(null)).isEqualTo("****");   // PhoneNormalizer.mask(null)
    }
}
