package in.schoolapp.billing;

import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionGuardInterceptorTest {

    @Mock SubscriptionService subscriptionService;

    @InjectMocks SubscriptionGuardInterceptor interceptor;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void preHandle_allowsGetEvenWhenSuspended() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant, UUID.randomUUID(), "PRINCIPAL");
        // GET should never be blocked, regardless of status — no SubscriptionService call needed.
        HttpServletRequest req = mockRequest("GET", "/api/v1/tenants/" + tenant + "/students");
        assertThat(interceptor.preHandle(req, mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void preHandle_allowsPostWhenActive() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant, UUID.randomUUID(), "PRINCIPAL");
        when(subscriptionService.effectiveStatus(tenant)).thenReturn(SubscriptionStatus.ACTIVE);
        HttpServletRequest req = mockRequest("POST", "/api/v1/tenants/" + tenant + "/students");
        assertThat(interceptor.preHandle(req, mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void preHandle_allowsPostWhenTrial() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant, UUID.randomUUID(), "PRINCIPAL");
        when(subscriptionService.effectiveStatus(tenant)).thenReturn(SubscriptionStatus.TRIAL);
        HttpServletRequest req = mockRequest("POST", "/api/v1/tenants/" + tenant + "/students");
        assertThat(interceptor.preHandle(req, mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void preHandle_blocksPostWhenSuspended() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant, UUID.randomUUID(), "PRINCIPAL");
        when(subscriptionService.effectiveStatus(tenant)).thenReturn(SubscriptionStatus.SUSPENDED);
        HttpServletRequest req = mockRequest("POST", "/api/v1/tenants/" + tenant + "/students");

        assertThatThrownBy(() -> interceptor.preHandle(req, mock(HttpServletResponse.class), new Object()))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.TENANT_SUSPENDED);
    }

    @Test
    void preHandle_blocksDeleteWhenCancelled() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant, UUID.randomUUID(), "PRINCIPAL");
        when(subscriptionService.effectiveStatus(tenant)).thenReturn(SubscriptionStatus.CANCELLED);
        HttpServletRequest req = mockRequest("DELETE", "/api/v1/tenants/" + tenant + "/students/abc");

        assertThatThrownBy(() -> interceptor.preHandle(req, mock(HttpServletResponse.class), new Object()))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.TENANT_SUSPENDED);
    }

    @Test
    void preHandle_allowsPlatformPathEvenWhenSuspended() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "SUPER_ADMIN");
        // Platform-admin endpoints must remain reachable so the admin can resume.
        HttpServletRequest req = mockRequest("POST", "/api/v1/platform/tenants/abc/resume");
        assertThat(interceptor.preHandle(req, mock(HttpServletResponse.class), new Object())).isTrue();
        // No SubscriptionService interaction expected — early return on the allowlist.
    }

    @Test
    void preHandle_allowsAuthPathEvenWhenSuspended() {
        // No tenant context set on auth endpoints; still always allowed.
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/otp/send");
        assertThat(interceptor.preHandle(req, mock(HttpServletResponse.class), new Object())).isTrue();
    }

    private HttpServletRequest mockRequest(String method, String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getMethod()).thenReturn(method);
        lenient().when(req.getRequestURI()).thenReturn(uri);
        return req;
    }
}
