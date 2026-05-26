package in.schoolapp.school.dto;

/**
 * Returned from the public signup endpoint. Bundles everything the frontend needs to move
 * immediately to the OTP login screen.
 */
public record SchoolSignupResponse(
    SchoolResponse school,
    StaffResponse principal,
    AcademicYearResponse academicYear,
    String nextStep
) {}
