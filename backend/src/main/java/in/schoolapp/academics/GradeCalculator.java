package in.schoolapp.academics;

import in.schoolapp.school.entity.Board;
import org.springframework.stereotype.Component;

/**
 * Board-specific grade lookup. Called from {@link MarksService} during upsert so the stored
 * {@code grade} on an {@link in.schoolapp.academics.entity.ExamMark} is always consistent with
 * its percentage — teachers can't accidentally save inconsistent grades.
 * <p>
 * CBSE uses A1/A2/B1…E per CBSE Circular 20/2019. Other boards fall back to generic
 * percentage-derived letters (A–F) until their specific scales are wired in.
 */
@Component
public class GradeCalculator {

    /**
     * @param obtained percentage (0.0–100.0) or {@code null} if absent/unset
     * @param board   tenant's board configuration
     * @return grade string, or {@code null} if {@code obtained} is null or negative
     */
    public String calculate(Double obtained, Board board) {
        if (obtained == null || obtained < 0) return null;
        return switch (board) {
            case CBSE, ICSE -> cbseGrade(obtained);
            default -> genericGrade(obtained);
        };
    }

    private static String cbseGrade(double pct) {
        if (pct >= 91) return "A1";
        if (pct >= 81) return "A2";
        if (pct >= 71) return "B1";
        if (pct >= 61) return "B2";
        if (pct >= 51) return "C1";
        if (pct >= 41) return "C2";
        if (pct >= 33) return "D";
        return "E";
    }

    private static String genericGrade(double pct) {
        if (pct >= 90) return "A";
        if (pct >= 80) return "B";
        if (pct >= 70) return "C";
        if (pct >= 60) return "D";
        if (pct >= 40) return "E";
        return "F";
    }
}
