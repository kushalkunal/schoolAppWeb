package in.schoolapp.school.dto;

import jakarta.validation.constraints.Size;

/**
 * Every field is optional — the caller sends only what should change. Null fields are left
 * untouched; empty strings clear the current value. Identifiers (phone, email, board) are NOT
 * updatable via this endpoint — they require a separate re-verification flow.
 */
public record UpdateSchoolRequest(
    @Size(max = 255) String name,
    @Size(max = 255) String principalName,
    String address,
    @Size(max = 100) String city,
    @Size(max = 100) String state,
    @Size(max = 10) String pincode,
    @Size(max = 15) String whatsappNumber
) {}
