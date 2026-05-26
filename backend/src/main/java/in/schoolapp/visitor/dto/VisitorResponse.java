package in.schoolapp.visitor.dto;

import in.schoolapp.visitor.entity.Visitor;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VisitorResponse(
    UUID id, String name, String phone, String purpose,
    UUID hostStaffId, UUID hostStudentId,
    String badgeNumber, String photoUrl,
    OffsetDateTime inAt, OffsetDateTime outAt,
    String notes
) {
    public static VisitorResponse from(Visitor v) {
        return new VisitorResponse(
            v.getId(), v.getName(), v.getPhone(), v.getPurpose(),
            v.getHostStaffId(), v.getHostStudentId(),
            v.getBadgeNumber(), v.getPhotoUrl(),
            v.getInAt(), v.getOutAt(),
            v.getNotes()
        );
    }
}
