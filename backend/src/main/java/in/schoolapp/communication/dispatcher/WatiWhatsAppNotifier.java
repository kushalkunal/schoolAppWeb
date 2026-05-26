package in.schoolapp.communication.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.PhoneNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real WhatsApp sender for WATI (live-mt-server.wati.io). Activated by
 * {@code app.whatsapp.provider=WATI}.
 *
 * <p><strong>Slice 3 change:</strong> credentials are resolved <em>per send</em> via
 * {@link WhatsAppConfigResolver} — tenant DB first, JVM-global env vars as fallback. The
 * constructor used to validate base-url/token; that gate moved to send time so a JVM can boot
 * with no global creds and serve schools that have per-tenant configs. Sends without any
 * resolvable config degrade to a log line and return {@code null} (no WATI call attempted).
 *
 * <p>Per-tenant {@link RestClient}s are cached by (baseUrl, token) so a school with steady
 * creds reuses the same client across sends; rotating creds creates a new entry.
 *
 * <p>Phone format: WATI expects country-code-prefixed digits, no {@code +} sign —
 * {@code "9876543210"} becomes {@code "919876543210"}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "WATI")
public class WatiWhatsAppNotifier implements RawWhatsAppNotifier {

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final WhatsAppConfigResolver configResolver;

    /**
     * Per-creds RestClient cache. Key = {@code baseUrl + "|" + tokenHashHex} so rotated creds
     * land on a fresh client instead of reusing the old one.
     */
    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public WatiWhatsAppNotifier(RestClient.Builder builder, ObjectMapper objectMapper,
                                WhatsAppConfigResolver configResolver) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.configResolver = configResolver;
    }

    @Override
    public String send(WhatsAppMessage message) {
        UUID schoolId = message.audit() == null ? null : message.audit().schoolId();
        var credsOpt = configResolver.resolve(schoolId);
        if (credsOpt.isEmpty()) {
            // No tenant config + no JVM env fallback → cannot dispatch via WATI. We log
            // (so the operator can spot it during onboarding) and return null. The decorator
            // treats null messageId as SENT-without-correlation, which is correct here since
            // we did intentionally drop the message.
            log.warn("[WATI-SKIP type={}] no WhatsApp creds for school={} — message dropped",
                message.type(), schoolId);
            return null;
        }
        WhatsAppConfigResolver.WatiCreds creds = credsOpt.get();
        RestClient client = clientFor(creds);

        String phoneDigits = wati91Digits(message.toPhone());
        String response = client.post()
            .uri("/api/v1/sendSessionMessage/{phone}", phoneDigits)
            .body(Map.of("messageText", message.body()))
            .retrieve()
            .body(String.class);
        log.debug("[WATI-SENT type={}] school={} to={} bytes={}",
            message.type(), schoolId, PhoneNormalizer.mask(message.toPhone()),
            message.body() == null ? 0 : message.body().length());
        return extractMessageId(response);
    }

    /** Reuse a single RestClient per credential set; new creds get a fresh client. */
    private RestClient clientFor(WhatsAppConfigResolver.WatiCreds creds) {
        String cacheKey = creds.baseUrl() + "|" + Integer.toHexString(creds.token().hashCode());
        return clientCache.computeIfAbsent(cacheKey, k -> builder
            .baseUrl(creds.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + creds.token())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build());
    }

    /** WATI returns {@code {"result": true, "messages": [{"whatsappMessageId": "..."}]}}. */
    private String extractMessageId(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode first = root.path("messages").path(0);
            JsonNode id = first.get("whatsappMessageId");
            return id == null || id.isNull() ? null : id.asText();
        } catch (Exception e) {
            log.debug("Could not extract WATI messageId from response: {}", e.getMessage());
            return null;
        }
    }

    /** "9876543210" → "919876543210" (drop +, prefix 91 if the 10-digit form is passed). */
    private String wati91Digits(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) return "91" + digits;
        return digits;
    }
}
