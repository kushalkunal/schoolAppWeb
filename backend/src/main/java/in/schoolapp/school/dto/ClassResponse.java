package in.schoolapp.school.dto;

import in.schoolapp.school.entity.SchoolClass;

import java.util.List;
import java.util.UUID;

public record ClassResponse(
    UUID id,
    String name,
    int sortOrder,
    List<SectionResponse> sections
) {
    public static ClassResponse from(SchoolClass c, List<SectionResponse> sections) {
        return new ClassResponse(c.getId(), c.getName(), c.getSortOrder(), sections);
    }
}
