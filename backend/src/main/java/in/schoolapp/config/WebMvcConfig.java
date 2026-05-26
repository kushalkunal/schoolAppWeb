package in.schoolapp.config;

import in.schoolapp.billing.SubscriptionGuardInterceptor;
import in.schoolapp.common.TenantInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    /**
     * Lazily resolved so {@code @WebMvcTest} slice tests (which exclude {@code @Component}
     * beans from the auth/billing modules) don't fail to wire this configuration. In production
     * the bean is always present and the interceptor registers; in slice tests it's absent and
     * we simply skip registration.
     */
    private final ObjectProvider<SubscriptionGuardInterceptor> subscriptionGuardProvider;

    public WebMvcConfig(TenantInterceptor tenantInterceptor,
                        ObjectProvider<SubscriptionGuardInterceptor> subscriptionGuardProvider) {
        this.tenantInterceptor = tenantInterceptor;
        this.subscriptionGuardProvider = subscriptionGuardProvider;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Order matters: TenantInterceptor first (validates path tenantId vs JWT and populates
        // TenantContext), then SubscriptionGuardInterceptor (reads TenantContext, blocks
        // mutations when subscription is suspended/cancelled).
        registry.addInterceptor(tenantInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/v1/auth/**", "/actuator/**", "/api/v1/platform/**")
            .order(0);

        subscriptionGuardProvider.ifAvailable(guard ->
            registry.addInterceptor(guard)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/**", "/actuator/**", "/api/v1/platform/**")
                .order(10)
        );
    }
}
