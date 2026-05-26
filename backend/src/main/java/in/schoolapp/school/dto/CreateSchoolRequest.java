package in.schoolapp.school.dto;

import in.schoolapp.school.entity.Board;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Step-1 onboarding payload. Phone and email are both optional at the bean-validation layer;
 * the service enforces "at least one, per {@code app.signup.channel}" since the rule depends
 * on runtime config. Everything else can be filled in later via the settings wizard.
 */
public record CreateSchoolRequest(
    @NotBlank @Size(max = 255) String schoolName,
    @NotBlank @Size(max = 255) String principalName,
    String phone,
    @Size(max = 255) String email,
    @NotBlank @Size(max = 100) String state,
    @NotNull Board board,
    @Size(max = 100) String city
) {}
