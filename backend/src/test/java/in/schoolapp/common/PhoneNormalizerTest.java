package in.schoolapp.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNormalizerTest {

    @Test
    void normalize_plainTenDigit_returnsAsIs() {
        assertThat(PhoneNormalizer.normalize("9876543210")).isEqualTo("9876543210");
    }

    @Test
    void normalize_stripsSpacesAndDashes() {
        assertThat(PhoneNormalizer.normalize("98765 43210")).isEqualTo("9876543210");
        assertThat(PhoneNormalizer.normalize("98765-43210")).isEqualTo("9876543210");
    }

    @Test
    void normalize_dropsCountryCode() {
        assertThat(PhoneNormalizer.normalize("+91 9876543210")).isEqualTo("9876543210");
        assertThat(PhoneNormalizer.normalize("919876543210")).isEqualTo("9876543210");
    }

    @Test
    void normalize_dropsLeadingZero() {
        assertThat(PhoneNormalizer.normalize("09876543210")).isEqualTo("9876543210");
    }

    @Test
    void normalize_rejectsNonMobilePrefix() {
        // Indian mobiles start with 6-9; landlines/invalid must fail
        assertThatThrownBy(() -> PhoneNormalizer.normalize("2345678910"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void normalize_rejectsEmpty() {
        assertThatThrownBy(() -> PhoneNormalizer.normalize(""))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> PhoneNormalizer.normalize(null))
            .isInstanceOf(AppException.class);
    }

    @Test
    void mask_hidesMiddleDigits() {
        assertThat(PhoneNormalizer.mask("9876543210")).isEqualTo("98765****10");
    }

    @Test
    void toE164_prefixesWith91() {
        assertThat(PhoneNormalizer.toE164("9876543210")).isEqualTo("+919876543210");
    }
}
