package in.schoolapp.common.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Activates Hibernate multi-tenancy so every session is bound to the caller's tenant at the JDBC
 * connection layer (see {@link RlsTenantConnectionProvider}) and the V37 row-level-security
 * policies enforce isolation. Registering a tenant resolver + connection provider is all Hibernate
 * 6 needs to switch multi-tenancy on.
 * <p>
 * Kill switch: set {@code app.multitenancy.rls-enabled=false} to skip wiring entirely. With the
 * provider unwired the app connects as its normal (superuser-capable) role, which bypasses RLS —
 * i.e. the app behaves exactly as before this change. The V37 migration is inert on its own, so
 * this toggle is a safe, instant rollback if connection-level binding ever misbehaves at startup.
 */
@Configuration
@ConditionalOnProperty(name = "app.multitenancy.rls-enabled", havingValue = "true", matchIfMissing = true)
public class MultiTenancyConfig {

    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer(DataSource dataSource) {
        RlsTenantConnectionProvider connectionProvider = new RlsTenantConnectionProvider(dataSource);
        TenantIdentifierResolver tenantResolver = new TenantIdentifierResolver();
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
