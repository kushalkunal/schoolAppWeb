package in.schoolapp.config;

import in.schoolapp.common.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * Populates {@code BaseEntity.createdByStaffId} via JPA auditing. Reads the current staff
     * from {@link TenantContext} — populated by the JWT filter. Returns empty for system-driven
     * writes (migrations, scheduled jobs), which is a valid "no auditor" state.
     */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(TenantContext.getStaffId());
    }

    /**
     * Supplies {@link OffsetDateTime} for {@code @CreatedDate}/{@code @LastModifiedDate} fields.
     * Spring Data's default provider returns {@link java.time.LocalDateTime} which is incompatible
     * with {@code BaseEntity.createdAt}'s {@code OffsetDateTime} typing — failing every insert
     * against Postgres with "Cannot convert unsupported date type".
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
