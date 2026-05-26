package in.schoolapp.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMatchingSimilarityTest {

    @Test
    void identicalNames_returns_one() {
        assertThat(EntityMatchingService.similarity("Rohan Sharma", "Rohan Sharma"))
            .isEqualTo(1.0);
    }

    @Test
    void caseDifferenceIgnored() {
        assertThat(EntityMatchingService.similarity("rohan sharma", "ROHAN SHARMA"))
            .isEqualTo(1.0);
    }

    @Test
    void whitespaceTrimmed() {
        assertThat(EntityMatchingService.similarity("  Rohan Sharma  ", "Rohan Sharma"))
            .isEqualTo(1.0);
    }

    @Test
    void singleCharTypoStillHigh() {
        // OCR typically misses one char on handwritten names; should still be > 0.85
        double score = EntityMatchingService.similarity("Rohan Sharma", "Rohen Sharma");
        assertThat(score).isGreaterThan(0.85);
    }

    @Test
    void abbreviation_partialMatch() {
        // "Ankit S." vs "Ankit Sharma" — shows ambiguity expected
        double score = EntityMatchingService.similarity("Ankit S.", "Ankit Sharma");
        assertThat(score).isBetween(0.5, 0.85);
    }

    @Test
    void completelyDifferent_returnsLowScore() {
        double score = EntityMatchingService.similarity("Rohan Sharma", "Priya Patel");
        assertThat(score).isLessThan(0.5);
    }

    @Test
    void emptyStrings_returnZeroOrOne() {
        assertThat(EntityMatchingService.similarity(null, "Rohan")).isZero();
        assertThat(EntityMatchingService.similarity("Rohan", null)).isZero();
        assertThat(EntityMatchingService.similarity("", "")).isEqualTo(1.0);
    }
}
