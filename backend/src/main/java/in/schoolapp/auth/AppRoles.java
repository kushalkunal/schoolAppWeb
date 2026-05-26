package in.schoolapp.auth;

/**
 * String constants for {@code @PreAuthorize("hasAnyRole(...)")} annotations. The
 * {@link in.schoolapp.auth.JwtAuthFilter} prefixes each role with {@code ROLE_} before adding
 * it to the Spring Security context, so these bare names match what Spring expects in the
 * {@code hasRole} / {@code hasAnyRole} expressions.
 * <p>
 * Not an enum because enum values can't be referenced in annotation expressions. Keep in sync
 * with {@link in.schoolapp.school.entity.StaffRole}.
 */
public final class AppRoles {

    private AppRoles() {}

    public static final String PRINCIPAL = "PRINCIPAL";
    public static final String SCHOOL_OWNER = "SCHOOL_OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String CLASS_TEACHER = "CLASS_TEACHER";
    public static final String SUBJECT_TEACHER = "SUBJECT_TEACHER";
    public static final String ACCOUNTANT = "ACCOUNTANT";

    // Common groupings — use these in @PreAuthorize expressions. Annotations can't use
    // concatenation, so the literal string is pre-built here.
    public static final String OWNER_OR_ADMIN = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN')";
    public static final String ANY_TEACHER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')";
    public static final String FEE_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','ACCOUNTANT')";
    public static final String ATTENDANCE_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER')";
    public static final String MARKS_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')";
}
