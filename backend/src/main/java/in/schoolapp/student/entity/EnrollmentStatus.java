package in.schoolapp.student.entity;

public enum EnrollmentStatus {
    ACTIVE,
    LEFT,
    GRADUATED,
    /** Promoted to the next class — the old enrollment is closed, a new one is created. */
    PROMOTED,
    /** Retained in the same class for the next year (failed). */
    RETAINED
}
