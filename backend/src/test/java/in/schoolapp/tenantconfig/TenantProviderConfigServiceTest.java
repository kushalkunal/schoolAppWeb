package in.schoolapp.tenantconfig;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.tenantconfig.entity.TenantProviderConfig;
import in.schoolapp.tenantconfig.repository.TenantProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TenantProviderConfigServiceTest {

    @Mock TenantProviderConfigRepository repository;

    SecretCipher cipher;
    TenantProviderConfigService service;

    UUID schoolId;

    @BeforeEach
    void setUp() {
        cipher = new SecretCipher("unit-test-key-at-least-16-chars-long");
        cipher.init();
        service = new TenantProviderConfigService(repository, cipher);
        schoolId = UUID.randomUUID();
    }

    @Test
    void set_encryptsSensitiveValues_keepsPlainOnesPlain() {
        Map<String, Object> in = new HashMap<>();
        in.put("base_url", "https://live-mt-server.wati.io/123");
        in.put("token", "live-wati-bearer");
        in.put("api_token", "another-secret");
        in.put("rate_per_second", 5);

        when(repository.findBySchoolIdAndConcern(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.empty());
        when(repository.save(any(TenantProviderConfig.class))).thenAnswer(inv -> {
            TenantProviderConfig t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        service.set(schoolId, ProviderConcern.WHATSAPP, ProviderType.WATI, in, "test", UUID.randomUUID());

        ArgumentCaptor<TenantProviderConfig> cap = ArgumentCaptor.forClass(TenantProviderConfig.class);
        verify(repository).save(cap.capture());
        Map<String, Object> saved = cap.getValue().getConfig();

        // Non-secret keys stay plain.
        assertThat(saved.get("base_url")).isEqualTo("https://live-mt-server.wati.io/123");
        assertThat(saved.get("rate_per_second")).isEqualTo(5);

        // Secret keys are encrypted on disk.
        assertThat((String) saved.get("token")).startsWith("enc:v1:");
        assertThat((String) saved.get("api_token")).startsWith("enc:v1:");
    }

    @Test
    void getActive_returnsDecryptedConfig() {
        TenantProviderConfig row = new TenantProviderConfig();
        row.setId(UUID.randomUUID());
        row.setSchoolId(schoolId);
        row.setConcern(ProviderConcern.PAYMENT);
        row.setProvider(ProviderType.STRIPE);
        row.setActive(true);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("secret_key", cipher.encrypt("sk_live_xyz"));
        cfg.put("currency", "INR");
        row.setConfig(cfg);
        when(repository.findBySchoolIdAndConcern(schoolId, ProviderConcern.PAYMENT))
            .thenReturn(Optional.of(row));

        TenantProviderConfigService.ResolvedConfig got =
            service.getActive(schoolId, ProviderConcern.PAYMENT).orElseThrow();
        assertThat(got.config().get("secret_key")).isEqualTo("sk_live_xyz");
        assertThat(got.config().get("currency")).isEqualTo("INR");
    }

    @Test
    void getActiveMasked_returnsMaskedForSensitiveFields() {
        TenantProviderConfig row = new TenantProviderConfig();
        row.setId(UUID.randomUUID());
        row.setSchoolId(schoolId);
        row.setConcern(ProviderConcern.PAYMENT);
        row.setProvider(ProviderType.STRIPE);
        row.setActive(true);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("secret_key", cipher.encrypt("sk_live_xyz"));
        cfg.put("currency", "INR");
        row.setConfig(cfg);
        when(repository.findBySchoolIdAndConcern(schoolId, ProviderConcern.PAYMENT))
            .thenReturn(Optional.of(row));

        TenantProviderConfigService.ResolvedConfig got =
            service.getActiveMasked(schoolId, ProviderConcern.PAYMENT).orElseThrow();
        // Secret masked; non-secret untouched.
        assertThat(got.config().get("secret_key")).isEqualTo("********");
        assertThat(got.config().get("currency")).isEqualTo("INR");
    }

    @Test
    void set_rejectsProviderNotValidForConcern() {
        // GEMINI is an LLM provider, not a PAYMENT provider — must be rejected.
        assertThatThrownBy(() ->
            service.set(schoolId, ProviderConcern.PAYMENT, ProviderType.GEMINI, Map.of(), null, null))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void set_replacesExistingRow() {
        TenantProviderConfig existing = new TenantProviderConfig();
        existing.setId(UUID.randomUUID());
        existing.setSchoolId(schoolId);
        existing.setConcern(ProviderConcern.WHATSAPP);
        existing.setProvider(ProviderType.LOGGING);
        existing.setConfig(new HashMap<>());
        existing.setActive(true);
        when(repository.findBySchoolIdAndConcern(schoolId, ProviderConcern.WHATSAPP))
            .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantProviderConfig out = service.set(schoolId, ProviderConcern.WHATSAPP,
            ProviderType.WATI, Map.of("token", "t"), "swap", UUID.randomUUID());

        assertThat(out.getProvider()).isEqualTo(ProviderType.WATI);
        // Same row id — replace, not insert.
        assertThat(out.getId()).isEqualTo(existing.getId());
        // verified_at reset on credential change.
        assertThat(out.getVerifiedAt()).isNull();
    }
}
