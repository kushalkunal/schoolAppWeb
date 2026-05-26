package in.schoolapp.academics;

import in.schoolapp.academics.entity.ExamMark;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExamMarkPercentageTest {

    @Test
    void percentage_normalCase() {
        ExamMark m = mark(new BigDecimal("100"), new BigDecimal("85"), false);
        assertThat(m.percentage()).isEqualTo(85.0);
    }

    @Test
    void percentage_fractionalMax() {
        ExamMark m = mark(new BigDecimal("25"), new BigDecimal("22"), false);
        assertThat(m.percentage()).isEqualTo(88.0);
    }

    @Test
    void percentage_absentReturnsNull() {
        ExamMark m = mark(new BigDecimal("100"), new BigDecimal("85"), true);
        assertThat(m.percentage()).isNull();
    }

    @Test
    void percentage_nullObtainedReturnsNull() {
        ExamMark m = mark(new BigDecimal("100"), null, false);
        assertThat(m.percentage()).isNull();
    }

    @Test
    void percentage_zeroMaxReturnsNull() {
        ExamMark m = mark(BigDecimal.ZERO, new BigDecimal("10"), false);
        assertThat(m.percentage()).isNull();
    }

    private ExamMark mark(BigDecimal max, BigDecimal obtained, boolean absent) {
        ExamMark m = new ExamMark();
        m.setMaxMarks(max);
        m.setObtainedMarks(obtained);
        m.setAbsent(absent);
        return m;
    }
}
