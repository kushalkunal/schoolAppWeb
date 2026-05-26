package in.schoolapp.auth;

import in.schoolapp.auth.config.OtpProperties;
import in.schoolapp.common.AppException;
import in.schoolapp.common.EmailNormalizer;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.communication.dispatcher.OtpDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * OTP lifecycle: generate → HMAC-hash → store in Redis (5-minute TTL) → hand off to the
 * configured {@link OtpDispatcher} for delivery. Verify reads the hash, constant-time
 * compares, and counts attempts.
 * <p>
 * HMAC-SHA256 is used (not bcrypt/argon2) because OTPs have tiny entropy (6 digits) but also
 * tiny TTL (5 min) and strict attempt caps (3). A fast keyed hash protects against Redis dump
 * leakage without adding per-verify latency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final OtpProperties props;
    private final OtpDispatcher dispatcher;

    /** Sends an OTP to the given identifier (phone or email). Returns the normalised identifier. */
    public String sendOtp(String rawIdentifier, IdentifierType type) {
        String identifier = normalize(rawIdentifier, type);

        // Rate limit: N sends per identifier per window, tracked via Redis INCR + TTL.
        String rateKey = rateLimitKey(identifier);
        Long sendCount = redis.opsForValue().increment(rateKey);
        if (sendCount != null && sendCount == 1L) {
            redis.expire(rateKey, Duration.ofMinutes(props.rateLimitWindowMinutes()));
        }
        if (sendCount != null && sendCount > props.maxSendsPerWindow()) {
            throw new AppException(ErrorCode.OTP_RATE_LIMITED,
                "Too many OTP requests. Please try again later.");
        }

        String otp = generateOtp();
        String hash = hmac(otp);

        String otpKey = otpKey(identifier);
        redis.opsForHash().put(otpKey, "hash", hash);
        redis.opsForHash().put(otpKey, "attempts", "0");
        redis.opsForHash().put(otpKey, "type", type.name());
        redis.opsForHash().put(otpKey, "createdAt", String.valueOf(System.currentTimeMillis()));
        redis.expire(otpKey, Duration.ofMinutes(props.ttlMinutes()));

        // Dispatch via the configured implementation (logging in dev, WATI/SMTP in prod)
        dispatcher.dispatch(identifier, type, otp);
        return identifier;
    }

    /**
     * Verifies the submitted OTP and returns the normalised identifier on success. The caller
     * is responsible for looking up the user record after this returns.
     */
    public String verifyOtp(String rawIdentifier, IdentifierType type, String submittedOtp) {
        String identifier = normalize(rawIdentifier, type);
        String otpKey = otpKey(identifier);

        String storedHash = (String) redis.opsForHash().get(otpKey, "hash");
        if (storedHash == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED, "OTP has expired or was never requested");
        }

        Long attempts = redis.opsForHash().increment(otpKey, "attempts", 1L);
        if (attempts != null && attempts > props.maxVerifyAttempts()) {
            redis.delete(otpKey);
            throw new AppException(ErrorCode.OTP_INVALID,
                "Too many incorrect attempts. Request a new OTP.");
        }

        String submittedHash = hmac(submittedOtp);
        if (!MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                submittedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.OTP_INVALID, "Incorrect OTP");
        }

        // Success: burn the OTP (single use)
        redis.delete(otpKey);
        return identifier;
    }

    // ---- internals ----

    private String normalize(String raw, IdentifierType type) {
        return switch (type) {
            case PHONE -> PhoneNormalizer.normalize(raw);
            case EMAIL -> EmailNormalizer.normalize(raw);
        };
    }

    private String otpKey(String identifier) {
        return "otp:code:" + identifier;
    }

    private String rateLimitKey(String identifier) {
        return "otp:rate:" + identifier;
    }

    private String generateOtp() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(props.hmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC algorithm unavailable", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalStateException("Invalid HMAC key", e);
        }
    }
}
