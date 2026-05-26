package in.schoolapp.notification.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FCM HTTP v1 push sender. Active when {@code app.push.provider=FCM}.
 *
 * <p>Authentication uses an OAuth 2.0 access token derived from the Google service-account
 * JSON. The token is acquired on demand via the JWT bearer flow, cached for 50 minutes
 * (its real lifetime is 60), and refreshed lazily. Per-send overhead is one HTTP call
 * (FCM send) when the cache is warm, plus a JWT signing + token exchange when cold.
 *
 * <p>This impl avoids the heavy {@code firebase-admin} SDK — the project Already depends on
 * Jackson + Spring's RestClient, which is all we need.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "FCM")
@RequiredArgsConstructor
public class FcmPushNotifier implements PushNotifier {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final PushConfigResolver resolver;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.create();
    private final Map<String, CachedToken> tokenCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public int sendToTokens(UUID schoolId, List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) return 0;
        var resolved = resolver.resolve(schoolId)
            .filter(r -> ProviderType.FCM.equals(r.provider()))
            .map(PushConfigResolver.ResolvedPush::fcm)
            .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                "No FCM credentials for tenant " + schoolId));

        String accessToken = accessTokenFor(resolved.projectId(), resolved.serviceAccountJson());
        String sendUrl = "https://fcm.googleapis.com/v1/projects/" + resolved.projectId() + "/messages:send";

        int accepted = 0;
        // HTTP v1 has no batch endpoint — one POST per token. Acceptable for low fan-outs; a
        // future enhancement is to use the legacy /batch endpoint or fan out to a thread pool.
        for (String token : tokens) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("token", token);
                Map<String, String> notification = new HashMap<>();
                notification.put("title", title);
                notification.put("body", body);
                message.put("notification", notification);
                if (data != null && !data.isEmpty()) message.put("data", data);

                Map<String, Object> envelope = Map.of("message", message);

                http.post().uri(sendUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(envelope)
                    .retrieve()
                    .toBodilessEntity();
                accepted++;
            } catch (Exception e) {
                log.warn("[FCM] send failed school={} token=…{} reason={}",
                    schoolId, mask(token), e.getMessage());
            }
        }
        log.info("[PUSH-SEND provider=FCM] school={} accepted={}/{}", schoolId, accepted, tokens.size());
        return accepted;
    }

    @Override
    public String providerName() { return ProviderType.FCM; }

    // ---------------- OAuth helpers ----------------

    /**
     * Returns a cached access token (per service-account JSON) if still valid, else
     * exchanges a freshly-signed JWT for one.
     */
    private String accessTokenFor(String projectId, String saJson) {
        String cacheKey = saJson.hashCode() + ":" + projectId;
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cached.token;
        }
        try {
            JsonNode sa = objectMapper.readTree(saJson);
            String clientEmail = sa.path("client_email").asText();
            String privateKeyPem = sa.path("private_key").asText();
            String jwt = signJwt(clientEmail, privateKeyPem);

            String body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt;
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(TOKEN_URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(body)
                .retrieve()
                .body(Map.class);

            String token = String.valueOf(resp.get("access_token"));
            int expiresIn = ((Number) resp.getOrDefault("expires_in", 3600)).intValue();
            Instant exp = Instant.now().plusSeconds(expiresIn);
            tokenCache.put(cacheKey, new CachedToken(token, exp));
            return token;
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "FCM access-token exchange failed: " + e.getMessage(), e);
        }
    }

    /** Builds a JWS (RS256) for the Google OAuth jwt-bearer grant. */
    private static String signJwt(String clientEmail, String privateKeyPem) throws Exception {
        long now = Instant.now().getEpochSecond();
        String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String claimsJson = String.format(
            "{\"iss\":\"%s\",\"scope\":\"%s\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d}",
            clientEmail, SCOPE, TOKEN_URL, now, now + 3600);

        String h = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String c = base64Url(claimsJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = h + "." + c;

        PrivateKey pk = parseRsaPrivateKey(privateKeyPem);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pk);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String s = base64Url(sig.sign());

        return signingInput + "." + s;
    }

    private static PrivateKey parseRsaPrivateKey(String pem) throws Exception {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(body);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String mask(String token) {
        return token == null || token.length() < 6 ? "****" : token.substring(token.length() - 6);
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
