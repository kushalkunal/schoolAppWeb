package in.schoolapp.academics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the admin "unlock results" endpoint.
 * Requires a non-empty reason for the audit trail.
 */
public record UnlockResultRequest(
    @NotBlank(message = "A reason is required when unlocking results")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    String reason
) {}
