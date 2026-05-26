package in.schoolapp.tenantconfig;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Field-level AES-GCM-256 cipher used to encrypt sensitive values inside
 * {@code tenant_provider_configs.config}. The wire format is:
 *
 * <pre>
 *   "enc:v1:" || base64( 12-byte-nonce || ciphertext+tag )
 * </pre>
 *
 * <p>The {@code enc:v1:} prefix makes encrypted values distinguishable from plaintext so
 * {@link #decryptIfNeeded(String)} can no-op on already-plain strings. It also reserves a
 * version channel for future cipher rotations.
 *
 * <p>Key source: {@code app.tenantconfig.encryption-key} env var. SHA-256 of the value is
 * used as the AES-256 key — so any length input is accepted, but a low-entropy key is a
 * low-entropy key. Production deployments should set a 32+ char random value (or pull from
 * a KMS); the dev default is a hard-coded string so the app boots without configuration.
 *
 * <p>This is application-level encryption only. Postgres TDE + at-rest disk encryption are
 * complementary — this defends against a database dump leaking secrets, not against an
 * attacker with JVM heap access.
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String ENC_PREFIX = "enc:v1:";
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String configuredKey;
    private SecretKey aesKey;

    public SecretCipher(@Value("${app.tenantconfig.encryption-key:dev-only-tenantconfig-key-CHANGE-ME-IN-PROD}")
                        String key) {
        this.configuredKey = key;
    }

    @PostConstruct
    void init() {
        if (configuredKey == null || configuredKey.length() < 16) {
            throw new IllegalStateException(
                "app.tenantconfig.encryption-key must be >= 16 chars");
        }
        // SHA-256 the input to a fixed 256-bit AES key. Means we accept arbitrary-length
        // keys; the entropy is whatever the operator put in.
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(hash, "AES");
            if (configuredKey.startsWith("dev-only-")) {
                log.warn("SecretCipher using DEV default key — set app.tenantconfig.encryption-key in prod");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES key", e);
        }
    }

    /** Encrypts a plaintext value. {@code null} → {@code null}. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (plaintext.startsWith(ENC_PREFIX)) return plaintext;  // already encrypted
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to encrypt secret", e);
        }
    }

    /** Decrypts a value previously produced by {@link #encrypt(String)}; passes plaintext through unchanged. */
    public String decryptIfNeeded(String value) {
        if (value == null || !value.startsWith(ENC_PREFIX)) return value;
        try {
            byte[] raw = Base64.getDecoder().decode(value.substring(ENC_PREFIX.length()));
            if (raw.length <= NONCE_BYTES) {
                throw new AppException(ErrorCode.INTERNAL_ERROR, "Ciphertext too short");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ct    = new byte[raw.length - NONCE_BYTES];
            System.arraycopy(raw, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(raw, NONCE_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Failed to decrypt secret (key rotated or value corrupted?)", e);
        }
    }
}
