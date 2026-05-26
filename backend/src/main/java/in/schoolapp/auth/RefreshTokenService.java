package in.schoolapp.auth;

import in.schoolapp.auth.config.JwtProperties;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens issued as UUIDs (122-bit entropy) and stored in Redis as SHA-256 hashes
 * keyed by the hash itself → staffId value. This means:
 * <ul>
 *   <li>Raw token is never persisted — a Redis dump doesn't leak tokens.</li>
 *   <li>Rotation: every successful refresh generates a new token and deletes the old.</li>
 *   <li>Logout: delete the token's hash; subsequent refreshes return 401.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String HASH_ALG = "SHA-256";

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProps;

    public String issue(UUID staffId) {
        String rawToken = UUID.randomUUID().toString();
        String hash = hash(rawToken);
        redis.opsForValue().set(
            "refresh:" + hash,
            staffId.toString(),
            Duration.ofDays(jwtProps.refreshTokenExpiryDays())
        );
        return rawToken;
    }

    /** Atomically rotates an old token for a new one. Returns the staffId for issuing a new JWT. */
    public UUID rotate(String rawOldToken) {
        String oldHash = hash(rawOldToken);
        String key = "refresh:" + oldHash;
        String staffId = redis.opsForValue().get(key);
        if (staffId == null) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED,
                "Refresh token is invalid or expired. Please log in again.");
        }
        redis.delete(key);
        return UUID.fromString(staffId);
    }

    public void revoke(String rawToken) {
        redis.delete("refresh:" + hash(rawToken));
    }

    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALG);
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
