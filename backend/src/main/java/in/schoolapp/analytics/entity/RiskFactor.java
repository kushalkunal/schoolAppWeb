package in.schoolapp.analytics.entity;

/**
 * Which signal contributes most to a student's at-risk score — surfaces on the dashboard so
 * staff know where to intervene first (a fee-driven flag and an attendance-driven flag need
 * different follow-ups).
 */
public enum RiskFactor {
    ATTENDANCE,
    FEE,
    MARKS
}
