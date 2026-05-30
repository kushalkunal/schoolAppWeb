package in.schoolapp.school.dto;

import in.schoolapp.school.entity.Section;

import java.util.UUID;

public record SectionResponse(
    UUID id,
    UUID classId,
    String className,
    String name,
    UUID classTeacherId,
    Integer maxStrength
) {
    /** Use when class name is not available (e.g., bulk list endpoints). */
    public static SectionResponse from(Section s) {
        return new SectionResponse(s.getId(), s.getClassId(), null, s.getName(),
            s.getClassTeacherId(), s.getMaxStrength());
    }

    /** Use when class name is available for richer display. */
    public static SectionResponse from(Section s, String className) {
        return new SectionResponse(s.getId(), s.getClassId(), className, s.getName(),
            s.getClassTeacherId(), s.getMaxStrength());
    }
}
