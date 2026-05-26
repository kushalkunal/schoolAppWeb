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
import org.springframework.util.Base64Utils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Twilio SMS sender. Active when {@code app.sms.provider=TWILIO} OR a tenant has TWILIO
 * configured (the resolver dispatches at send time).
 *
 * <p>Per-account RestClients are cached so we don't pay handshake cost on every send.
 * Credentials are resolved via {@link SmsConfigResolver} per-request — never stored in
 * the cache, only the configured BasicAuth header is.
 *
 * <p>Twilio SMS REST API:
 * {@code POST https://api.twilio.com/2010-04-01/Accounts/{AccountSid}/Messages.json}
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "TWILIO")
@RequiredArgsConstructor
public class TwilioSmsSender implements SmsSender {

    private static final String API_ROOT = "https://api.twilio.com/2010-04-01/Accounts/";

    private final SmsConfigResolver resolver;
    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public String send(UUID schoolId, String toPhone, String body) {
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.TWILIO.equals(r.provider()))
            .map(SmsConfigResolver.ResolvedSms::twilio)
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "No Twilio credentials configured for tenant " + schoolId));

        RestClient client = clientCache.computeIfAbsent(
            resolved.accountSid() + ":" + resolved.authToken(),
            k -> buildClient(resolved.accountSid(), resolved.authToken()));

        try {
            String url = API_ROOT + resolved.accountSid() + "/Messages.json";
            String form = UriComponentsBuilder.newInstance()
                .queryParam("To", toPhone)
                .queryParam("From", resolved.fromNumber())
                .queryParam("Body", body)
                .build().getQuery();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post().uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(form)
                .retrieve()
                .body(Map.class);

            String sid = resp != null ? String.valueOf(resp.get("sid")) : null;
            log.info("[SMS-SEND provider=TWILIO] school={} sid={} status={}", schoolId, sid,
                resp != null ? resp.get("status") : null);
            return sid != null ? sid : "twilio-unknown";
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Twilio send failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return ProviderType.TWILIO; }

    private static RestClient buildClient(String sid, String authToken) {
        String basicAuth = Base64Utils.encodeToString((sid + ":" + authToken).getBytes());
        return RestClient.builder()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
