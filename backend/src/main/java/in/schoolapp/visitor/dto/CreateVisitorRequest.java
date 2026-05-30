package in.schoolapp.visitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVisitorRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 30)  String phone,
    @Size(max = 255) String purpose,
    UUID   hostStaffId,
    UUID   hostStudentId,
    boolean studentPickup,
    @Size(max = 500) String pickupOverrideReason,
    @Size(max = 40)  String badgeNumber,
    String photoUrl,
    String notes
) {}
