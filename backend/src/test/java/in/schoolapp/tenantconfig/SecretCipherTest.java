package in.schoolapp.tenantconfig;

import in.schoolapp.common.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    SecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new SecretCipher("unit-test-key-at-least-16-chars-long");
        cipher.init();
    }

    @Test
    void encrypt_then_decrypt_roundTripsCleartext() {
        String plain = "sk_live_AbCdEf012345";
        String ciphered = cipher.encrypt(plain);

        assertThat(ciphered).startsWith("enc:v1:");
        assertThat(ciphered).isNotEqualTo(plain);
        assertThat(cipher.decryptIfNeeded(ciphered)).isEqualTo(plain);
    }

    @Test
    void encrypt_isNonDeterministic() {
        String plain = "secret";
        // GCM with a fresh nonce per call → ciphertexts differ.
        assertThat(cipher.encrypt(plain)).isNotEqualTo(cipher.encrypt(plain));
    }

    @Test
    void encrypt_passesNullThrough() {
        assertThat(cipher.encrypt(null)).isNull();
    }

    @Test
    void decryptIfNeeded_passesPlaintextThrough() {
        // No prefix → return unchanged. Useful so old plaintext rows still load post-deploy.
        assertThat(cipher.decryptIfNeeded("plaintext")).isEqualTo("plaintext");
        assertThat(cipher.decryptIfNeeded(null)).isNull();
    }

    @Test
    void encrypt_isIdempotent_onAlreadyEncryptedValue() {
        String once = cipher.encrypt("x");
        String twice = cipher.encrypt(once);
        // Idempotency: a value with the enc:v1: prefix is returned unchanged.
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void decryptIfNeeded_throwsOnCorruptedCiphertext() {
        assertThatThrownBy(() -> cipher.decryptIfNeeded("enc:v1:not-base64!!"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void init_rejectsShortKey() {
        SecretCipher c = new SecretCipher("short");
        assertThatThrownBy(c::init).isInstanceOf(IllegalStateException.class);
    }
}
