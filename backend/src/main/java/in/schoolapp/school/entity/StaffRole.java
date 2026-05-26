package in.schoolapp.school.entity;

/**
 * Roles map directly to Spring Security authorities (prefixed with {@code ROLE_} at filter time)
 * and to DDL {@code staff.role} VARCHAR values. Never rename — clients and JWT claims persist the
 * string value.
 */
public enum StaffRole {
    SUPER_ADMIN,        // platform-level (our team)
    SCHOOL_OWNER,       // full access, owner of the school
    PRINCIPAL,          // same as SCHOOL_OWNER for Phase 1
    ADMIN,              // all except school deletion + pricing
    CLASS_TEACHER,      // own section: all; other sections: read-only
    SUBJECT_TEACHER,    // marks entry for assigned subject/section only
    ACCOUNTANT,         // fee module only
    VIEWER              // read-only, no PII phone numbers
}
