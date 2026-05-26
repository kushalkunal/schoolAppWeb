package in.schoolapp.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailNormalizerTest {

    @Test
    void normalize_lowercasesAndTrims() {
        assertThat(EmailNormalizer.normalize("  User@Example.COM  ")).isEqualTo("user@example.com");
    }

    @Test
    void normalize_keepsValidLocalCharacters() {
        assertThat(EmailNormalizer.normalize("rajesh.kumar+school@domain.co.in"))
            .isEqualTo("rajesh.kumar+school@domain.co.in");
    }

    @Test
    void normalize_rejectsMissingAt() {
        assertThatThrownBy(() -> EmailNormalizer.normalize("noatsign.com"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void normalize_rejectsMissingTld() {
        assertThatThrownBy(() -> EmailNormalizer.normalize("user@domain"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void normalize_rejectsBlank() {
        assertThatThrownBy(() -> EmailNormalizer.normalize(""))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> EmailNormalizer.normalize(null))
            .isInstanceOf(AppException.class);
    }

    @Test
    void mask_hidesLocalPart() {
        assertThat(EmailNormalizer.mask("rajesh@example.com")).isEqualTo("ra****@example.com");
    }

    @Test
    void mask_shortLocalPartHiddenEntirely() {
        assertThat(EmailNormalizer.mask("a@b.com")).isEqualTo("****@b.com");
    }
}
