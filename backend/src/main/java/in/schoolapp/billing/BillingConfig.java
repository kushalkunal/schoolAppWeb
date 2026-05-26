package in.schoolapp.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link SubscriptionGuardInterceptor} as a Spring bean, but only when
 * {@link SubscriptionService} is present in the context. The conditional keeps
 * {@code @WebMvcTest} slice tests green — they exclude billing services and don't need
 * the guard.
 */
@Configuration
public class BillingConfig {

    @Bean
    @ConditionalOnBean(SubscriptionService.class)
    public SubscriptionGuardInterceptor subscriptionGuardInterceptor(SubscriptionService subscriptionService) {
        return new SubscriptionGuardInterceptor(subscriptionService);
    }
}
