package in.schoolapp.student.dto;

import java.util.List;
import java.util.UUID;

/**
 * One primary parent + all their linked children in this tenant. Powers the "family card" in
 * the quick-collect flow and sibling-aware notifications.
 */
public record FamilyViewResponse(
    UUID primaryParentId,
    String primaryParentName,
    String primaryParentPhone,
    String primaryParentEmail,
    List<FamilyMember> children
) {
    public record FamilyMember(
        UUID studentId,
        String displayName,
        UUID sectionId,
        String className,
        String sectionName
    ) {}
}
