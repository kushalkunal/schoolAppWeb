package in.schoolapp.auth;

/** Discriminates between the two login identifier kinds — used by OTP dispatch + lookup. */
public enum IdentifierType {
    PHONE,
    EMAIL
}
