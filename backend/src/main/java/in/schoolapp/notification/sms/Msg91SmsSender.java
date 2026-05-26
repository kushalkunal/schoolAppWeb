package in.schoolapp.notification.sms;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.tenantconfig.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MSG91 SMS sender — the dominant India-focused SMS gateway. Active when
 * {@code app.sms.provider=MSG91}.
 *
 * <p>MSG91 v5 Flow API:
 * {@code POST https://api.msg91.com/api/v5/flow/} with header {@code authkey: <auth_key>}.
 * Body is a JSON containing template_id, sender, recipients (with their variables).
 *
 * <p>This implementation sends transactional messages using a pre-approved DLT template.
 * Schools must register their template + sender ID with MSG91 first (DLT compliance).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "MSG91")
@RequiredArgsConstructor
public class Msg91SmsSender implements SmsSender {

    private static final String FLOW_URL = "https://api.msg91.com/api/v5/flow/";

    private final SmsConfigResolver resolver;
    private final RestClient restClient = RestClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    @Override
    public String send(UUID schoolId, String toPhone, String body) {
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.MSG91.equals(r.provider()))
            .map(SmsConfigResolver.ResolvedSms::msg91)
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "No MSG91 credentials configured for tenant " + schoolId));

        if (resolved.templateId() == null || resolved.templateId().isBlank()) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "MSG91 template_id missing — DLT-approved template required per Indian TRAI rules");
        }

        try {
            Map<String, Object> recipient = new HashMap<>();
            recipient.put("mobiles", normalisePhone(toPhone));
            // The variable name "var1" is the MSG91 convention for the first template placeholder.
            // Schools with multi-variable templates upload a custom template + variable mapping
            // via tenant config (extend ResolvedSms.msg91 if needed).
            recipient.put("var1", body);

            Map<String, Object> payload = new HashMap<>();
            payload.put("template_id", resolved.templateId());
            payload.put("sender", resolved.senderId());
            payload.put("short_url", 0);
            payload.put("recipients", List.of(recipient));

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.post()
                .uri(FLOW_URL)
                .header("authkey", resolved.authKey())
                .body(payload)
                .retrieve()
                .body(Map.class);

            String requestId = resp != null ? String.valueOf(resp.get("request_id")) : null;
            log.info("[SMS-SEND provider=MSG91] school={} requestId={} type={}",
                schoolId, requestId, resp != null ? resp.get("type") : null);
            return requestId != null ? requestId : "msg91-unknown";
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "MSG91 send failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return ProviderType.MSG91; }

    /** MSG91 expects E.164 without the leading '+': "919876543210". */
    private static String normalisePhone(String raw) {
        String s = raw.replaceAll("[^0-9]", "");
        // Add India country code if a bare 10-digit number was passed.
        if (s.length() == 10) s = "91" + s;
        return s;
    }
}
