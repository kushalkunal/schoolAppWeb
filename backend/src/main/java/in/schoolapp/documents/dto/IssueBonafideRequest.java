package in.schoolapp.documents.dto;

import jakarta.validation.constraints.Size;

public record IssueBonafideRequest(
    /** e.g. "passport application", "bus pass", "scholarship". Goes into the certificate body. */
    @Size(max = 200) String purpose,
    @Size(max = 40)  String certificateNumber
) {}
