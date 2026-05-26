package in.schoolapp.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_roundTrips() {
        UUID tenant = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        TenantContext.set(tenant, staff, "PRINCIPAL");

        assertThat(TenantContext.getTenantId()).isEqualTo(tenant);
        assertThat(TenantContext.getStaffId()).isEqualTo(staff);
        assertThat(TenantContext.getRole()).isEqualTo("PRINCIPAL");
        assertThat(TenantContext.isAuthenticated()).isTrue();
    }

    @Test
    void clear_removesAllState() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getStaffId()).isNull();
        assertThat(TenantContext.getRole()).isNull();
        assertThat(TenantContext.isAuthenticated()).isFalse();
    }

    @Test
    void validateTenant_matchingIdPasses() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant, UUID.randomUUID(), "PRINCIPAL");

        // should not throw
        TenantContext.validateTenant(tenant);
    }

    @Test
    void validateTenant_mismatchThrowsForbidden() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "PRINCIPAL");

        assertThatThrownBy(() -> TenantContext.validateTenant(UUID.randomUUID()))
            .isInstanceOf(AppException.class)
            .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    void validateTenant_noAuthThrowsUnauthorized() {
        // no set() call
        assertThatThrownBy(() -> TenantContext.validateTenant(UUID.randomUUID()))
            .isInstanceOf(AppException.class)
            .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.UNAUTHORIZED);
    }
}
