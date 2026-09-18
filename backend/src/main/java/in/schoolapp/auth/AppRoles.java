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
    public static final String LIBRARIAN = "LIBRARIAN";
    public static final String RECEPTIONIST = "RECEPTIONIST";

    // Common groupings — use these in @PreAuthorize expressions. Annotations can't use
    // concatenation, so the literal string is pre-built here.
    public static final String OWNER_OR_ADMIN = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN')";
    /** Front desk: admins plus the receptionist. Use on visitor logging + admission-enquiry
     *  intake. A pure superset of {@link #OWNER_OR_ADMIN} — only adds RECEPTIONIST, never
     *  removes an existing grant. */
    public static final String FRONT_DESK = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','RECEPTIONIST')";
    /** Narrower than {@link #OWNER_OR_ADMIN}: excludes ADMIN. Used as the checker gate for
     *  maker-checker approvals so an ADMIN who requested a discount/refund cannot also approve it. */
    public static final String OWNER_OR_PRINCIPAL = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL')";
    public static final String ANY_TEACHER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')";
    /** Every role that can be a staff member — use for self-service endpoints (own attendance, own leave). */
    public static final String ANY_STAFF = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER','ACCOUNTANT','LIBRARIAN','RECEPTIONIST')";
    public static final String FEE_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','ACCOUNTANT')";
    public static final String ATTENDANCE_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER')";
    public static final String MARKS_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')";
    public static final String LIBRARY_WRITER = "hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','LIBRARIAN')";
}
