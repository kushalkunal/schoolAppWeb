package in.schoolapp.academics;

import in.schoolapp.school.entity.Board;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GradeCalculatorTest {

    private final GradeCalculator calc = new GradeCalculator();

    @Test
    void cbse_boundaries() {
        // CBSE circular 20/2019 grades
        assertThat(calc.calculate(95.0, Board.CBSE)).isEqualTo("A1");
        assertThat(calc.calculate(91.0, Board.CBSE)).isEqualTo("A1");
        assertThat(calc.calculate(90.9, Board.CBSE)).isEqualTo("A2");
        assertThat(calc.calculate(81.0, Board.CBSE)).isEqualTo("A2");
        assertThat(calc.calculate(75.0, Board.CBSE)).isEqualTo("B1");
        assertThat(calc.calculate(65.0, Board.CBSE)).isEqualTo("B2");
        assertThat(calc.calculate(55.0, Board.CBSE)).isEqualTo("C1");
        assertThat(calc.calculate(45.0, Board.CBSE)).isEqualTo("C2");
        assertThat(calc.calculate(35.0, Board.CBSE)).isEqualTo("D");
        assertThat(calc.calculate(20.0, Board.CBSE)).isEqualTo("E");
        assertThat(calc.calculate(0.0, Board.CBSE)).isEqualTo("E");
    }

    @Test
    void icse_usesSameScaleAsCbse() {
        assertThat(calc.calculate(95.0, Board.ICSE)).isEqualTo("A1");
        assertThat(calc.calculate(32.0, Board.ICSE)).isEqualTo("E");
    }

    @Test
    void generic_aToF() {
        assertThat(calc.calculate(95.0, Board.STATE)).isEqualTo("A");
        assertThat(calc.calculate(85.0, Board.STATE)).isEqualTo("B");
        assertThat(calc.calculate(75.0, Board.STATE)).isEqualTo("C");
        assertThat(calc.calculate(65.0, Board.STATE)).isEqualTo("D");
        assertThat(calc.calculate(45.0, Board.STATE)).isEqualTo("E");
        assertThat(calc.calculate(35.0, Board.STATE)).isEqualTo("F");
    }

    @Test
    void nullInput_returnsNull() {
        assertThat(calc.calculate(null, Board.CBSE)).isNull();
        assertThat(calc.calculate(-1.0, Board.CBSE)).isNull();
    }
}
