package in.schoolapp.documents;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tamper-evident verification for issued PDF documents (admit card / report card / fee
 * receipt). Every generated document carries a QR code pointing at the public verify URL
 * with a signed token. Anyone (parent, exam invigilator, auditor) can scan it and confirm
 * the document was genuinely issued by this school and has not been altered.
 *
 * <p>The token is {@code base64url(payload) + "." + base64url(HMAC-SHA256(payload))}, where
 * payload is {@code schoolId|type|reference|issuedEpochSeconds}. We deliberately avoid a DB
 * round-trip on issue: the HMAC over the school+type+reference is enough to prove provenance,
 * because forging it requires the server secret. The reference (receipt no / admit-card no /
 * report-card id) lets a human cross-check against the school's records.
 *
 * <p>This is NOT a cryptographic PDF signature (PAdES/PKCS7) — it does not bind the exact
 * byte stream. It binds the document's <em>identity</em>. A visible signature image
 * (principal's signature, configured in branding) is rendered separately on the document.
 */
@Slf4j
@Service
public class DocumentVerificationService {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secret;
    private final String verifyBaseUrl;

    public DocumentVerificationService(
        @Value("${app.documents.verify-secret:${app.jwt.secret}}") String secret,
        @Value("${app.documents.verify-base-url:http://localhost:${server.port:8080}}") String verifyBaseUrl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.verifyBaseUrl = verifyBaseUrl.endsWith("/")
            ? verifyBaseUrl.substring(0, verifyBaseUrl.length() - 1) : verifyBaseUrl;
    }

    /** The full URL a QR code should encode for the given document. */
    public String verifyUrl(UUID schoolId, DocumentType type, String reference) {
        return verifyBaseUrl + "/api/v1/public/documents/verify?token=" + token(schoolId, type, reference);
    }

    /** Signed, URL-safe token embedding the document identity. */
    public String token(UUID schoolId, DocumentType type, String reference) {
        String ref = reference == null || reference.isBlank() ? "-" : reference;
        String payload = schoolId + "|" + type.name() + "|" + ref + "|" + Instant.now().getEpochSecond();
        String p = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + base64Url(hmac(p));
    }

    /** Validates a token and returns the decoded, verified document identity. */
    public VerificationResult verify(String token) {
        try {
            int dot = token.indexOf('.');
            if (dot <= 0) return VerificationResult.invalid();
            String p = token.substring(0, dot);
            String sig = token.substring(dot + 1);
            if (!constantTimeEquals(base64Url(hmac(p)), sig)) {
                return VerificationResult.invalid();
            }
            String payload = new String(Base64.getUrlDecoder().decode(p), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", 4);
            if (parts.length != 4) return VerificationResult.invalid();
            return new VerificationResult(true, UUID.fromString(parts[0]),
                DocumentType.valueOf(parts[1]), parts[2], Instant.ofEpochSecond(Long.parseLong(parts[3])));
        } catch (Exception e) {
            log.debug("Document token verification failed: {}", e.getMessage());
            return VerificationResult.invalid();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "HMAC failure", e);
        }
    }

    private static String base64Url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    public record VerificationResult(boolean valid, UUID schoolId, DocumentType type,
                                     String reference, Instant issuedAt) {
        static VerificationResult invalid() {
            return new VerificationResult(false, null, null, null, null);
        }

        public Map<String, Object> toResponse() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("valid", valid);
            if (valid) {
                m.put("schoolId", schoolId);
                m.put("documentType", type.name());
                m.put("reference", reference);
                m.put("issuedAt", issuedAt.toString());
            }
            return m;
        }
    }
}
