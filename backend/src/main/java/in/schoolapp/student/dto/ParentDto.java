package in.schoolapp.student.dto;

import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.ParentRelation;

import java.util.UUID;

public record ParentDto(
    UUID id,
    String name,
    String phone,
    String email,
    String relationType,
    ParentRelation relation,
    boolean primary
) {
    public static ParentDto from(Parent p, ParentRelation relation, boolean primary) {
        return new ParentDto(
            p.getId(),
            p.getName(),
            p.getPhone(),
            p.getEmail(),
            p.getRelationType(),
            relation,
            primary
        );
    }
}
