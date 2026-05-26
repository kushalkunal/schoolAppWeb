package in.schoolapp.academics.dto;

import java.util.List;
import java.util.UUID;

/**
 * What the frontend renders as a grid: roster (rows) × subjects (columns) with the cells
 * pre-populated by any existing marks. The teacher edits cells and submits via
 * {@link BulkMarksRequest}.
 */
public record MarksEntrySheetResponse(
    UUID examId,
    UUID sectionId,
    List<StudentRow> students,
    List<MarkResponse> existingMarks
) {
    public record StudentRow(
        UUID studentId,
        String displayName,
        String admissionNumber,
        Integer rollNumber
    ) {}
}
