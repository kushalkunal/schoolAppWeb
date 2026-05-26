package in.schoolapp.school.dto;

import in.schoolapp.school.entity.Section;

import java.util.UUID;

public record SectionResponse(
    UUID id,
    UUID classId,
    String name,
    UUID classTeacherId,
    Integer maxStrength
) {
    public static SectionResponse from(Section s) {
        return new SectionResponse(s.getId(), s.getClassId(), s.getName(),
            s.getClassTeacherId(), s.getMaxStrength());
    }
}
