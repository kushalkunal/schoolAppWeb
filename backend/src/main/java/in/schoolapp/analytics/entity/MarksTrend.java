package in.schoolapp.analytics.entity;

/**
 * Simple direction of marks from the previous exam to the most recent one. UNKNOWN when there
 * aren't enough published report cards to compare (new student, first exam of year, etc.).
 */
public enum MarksTrend {
    UP,
    DOWN,
    FLAT,
    UNKNOWN
}
