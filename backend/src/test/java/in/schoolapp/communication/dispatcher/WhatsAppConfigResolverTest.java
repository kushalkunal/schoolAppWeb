package in.schoolapp.communication.dispatcher;

import in.schoolapp.communication.dispatcher.config.WhatsAppProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppConfigResolverTest {

    @Mock TenantProviderConfigService tenantConfigService;

    @Test
    void tenantConfigWinsOverEnvFallback() {
        UUID schoolId = UUID.randomUUID();
        TenantProviderConfigService.ResolvedConfig tenantCfg =
            new TenantProviderConfigService.ResolvedConfig(
                UUID.randomUUID(), schoolId, ProviderConcern.WHATSAPP, ProviderType.WATI,
                Map.of("base_url", "https://tenant.wati", "token", "tenant-token"));
        when(tenantConfigService.getActive(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.of(tenantCfg));

        WhatsAppProperties props = whatsAppProps("https://env.wati", "env-token");
        WhatsAppConfigResolver resolver = new WhatsAppConfigResolver(tenantConfigService, props);

        WhatsAppConfigResolver.WatiCreds out = resolver.resolve(schoolId).orElseThrow();
        assertThat(out.baseUrl()).isEqualTo("https://tenant.wati");
        assertThat(out.token()).isEqualTo("tenant-token");
        assertThat(out.configId()).isNotNull();   // came from DB
    }

    @Test
    void fallsBackToEnv_whenNoTenantConfig() {
        UUID schoolId = UUID.randomUUID();
        when(tenantConfigService.getActive(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.empty());
        WhatsAppProperties props = whatsAppProps("https://env.wati", "env-token");
        WhatsAppConfigResolver resolver = new WhatsAppConfigResolver(tenantConfigService, props);

        WhatsAppConfigResolver.WatiCreds out = resolver.resolve(schoolId).orElseThrow();
        assertThat(out.baseUrl()).isEqualTo("https://env.wati");
        assertThat(out.token()).isEqualTo("env-token");
        assertThat(out.configId()).isNull();   // came from env
    }

    @Test
    void returnsEmpty_whenTenantPicksLogging() {
        // Explicit "mute outbound" — tenant chose LOGGING in their config.
        UUID schoolId = UUID.randomUUID();
        TenantProviderConfigService.ResolvedConfig tenantCfg =
            new TenantProviderConfigService.ResolvedConfig(
                UUID.randomUUID(), schoolId, ProviderConcern.WHATSAPP, ProviderType.LOGGING,
                Map.of());
        when(tenantConfigService.getActive(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.of(tenantCfg));
        // Env vars are set, but tenant LOGGING wins → no creds returned.
        WhatsAppProperties props = whatsAppProps("https://env.wati", "env-token");
        WhatsAppConfigResolver resolver = new WhatsAppConfigResolver(tenantConfigService, props);

        assertThat(resolver.resolve(schoolId)).isEmpty();
    }

    @Test
    void returnsEmpty_whenNothingConfigured() {
        UUID schoolId = UUID.randomUUID();
        when(tenantConfigService.getActive(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.empty());
        WhatsAppProperties props = whatsAppProps("", null);
        WhatsAppConfigResolver resolver = new WhatsAppConfigResolver(tenantConfigService, props);

        assertThat(resolver.resolve(schoolId)).isEmpty();
    }

    @Test
    void handlesNullSchoolId() {
        // OTP / pre-auth dispatch with no audit context — only env is consulted.
        WhatsAppProperties props = whatsAppProps("https://env.wati", "env-token");
        WhatsAppConfigResolver resolver = new WhatsAppConfigResolver(tenantConfigService, props);

        WhatsAppConfigResolver.WatiCreds out = resolver.resolve(null).orElseThrow();
        assertThat(out.baseUrl()).isEqualTo("https://env.wati");
    }

    @Test
    void tenantWatiMissingFields_fallsThroughToEnv() {
        UUID schoolId = UUID.randomUUID();
        TenantProviderConfigService.ResolvedConfig bad =
            new TenantProviderConfigService.ResolvedConfig(
                UUID.randomUUID(), schoolId, ProviderConcern.WHATSAPP, ProviderType.WATI,
                Map.of("base_url", ""));   // token missing entirely
        when(tenantConfigService.getActive(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.of(bad));
        WhatsAppProperties props = whatsAppProps("https://env.wati", "env-token");
        WhatsAppConfigResolver resolver = new WhatsAppConfigResolver(tenantConfigService, props);

        // Defensive: a half-configured tenant row drops through to env rather than throwing.
        WhatsAppConfigResolver.WatiCreds out = resolver.resolve(schoolId).orElseThrow();
        assertThat(out.baseUrl()).isEqualTo("https://env.wati");
    }

    private static WhatsAppProperties whatsAppProps(String baseUrl, String token) {
        return new WhatsAppProperties(
            WhatsAppProperties.Provider.WATI,
            new WhatsAppProperties.WatiConfig(baseUrl, token),
            "webhook-secret"
        );
    }
}
