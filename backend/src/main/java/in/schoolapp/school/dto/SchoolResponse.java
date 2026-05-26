package in.schoolapp.school.dto;

import in.schoolapp.school.entity.Board;
import in.schoolapp.school.entity.School;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SchoolResponse(
    UUID id,
    String name,
    String principalName,
    String phone,
    String email,
    String city,
    String state,
    Board board,
    boolean active,
    boolean waConfigured,
    OffsetDateTime createdAt
) {
    public static SchoolResponse from(School s) {
        return new SchoolResponse(
            s.getId(),
            s.getName(),
            s.getPrincipalName(),
            s.getPhone(),
            s.getEmail(),
            s.getCity(),
            s.getState(),
            s.getBoard(),
            s.isActive(),
            s.isWaConfigured(),
            s.getCreatedAt()
        );
    }
}
