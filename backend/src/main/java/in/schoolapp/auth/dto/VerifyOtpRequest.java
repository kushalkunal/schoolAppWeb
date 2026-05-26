package in.schoolapp.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Exactly one of {@code phone} or {@code email} must match whatever was used for send.
 */
public record VerifyOtpRequest(
    String phone,
    @Size(max = 255) String email,
    @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits") String otp
) {
    @AssertTrue(message = "Exactly one of phone or email must be provided")
    public boolean isExactlyOneProvided() {
        boolean hasPhone = phone != null && !phone.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        return hasPhone ^ hasEmail;
    }
}
