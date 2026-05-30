package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.ExamSubjectConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Marking scheme for one subject in one exam — all configured components. */
public record ExamStructureResponse(
    UUID subjectId,
    String subjectName,
    List<ComponentDto> components,
    BigDecimal totalMax
) {
    public record ComponentDto(
        UUID id,
        String componentName,
        BigDecimal maxMarks,
        BigDecimal passingMarks,
        int sortOrder
    ) {
        public static ComponentDto from(ExamSubjectConfig c) {
            return new ComponentDto(
                c.getId(),
                c.getComponentName(),
                c.getMaxMarks(),
                c.getPassingMarks(),
                c.getSortOrder()
            );
        }
    }
}
