package in.schoolapp.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Exactly one of {@code phone} or {@code email} must be present. The server resolves the
 * identifier kind from whichever field is populated; the caller needn't declare a channel.
 */
public record SendOtpRequest(
    String phone,
    @Size(max = 255) String email
) {
    @AssertTrue(message = "Exactly one of phone or email must be provided")
    public boolean isExactlyOneProvided() {
        boolean hasPhone = phone != null && !phone.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        return hasPhone ^ hasEmail;
    }
}
