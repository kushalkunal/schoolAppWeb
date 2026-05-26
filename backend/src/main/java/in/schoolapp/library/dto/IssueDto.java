package in.schoolapp.library.dto;

import in.schoolapp.library.entity.BookIssue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IssueDto(
    UUID id,
    @NotNull UUID bookId,
    @NotNull UUID studentId,
    OffsetDateTime issuedAt,
    @NotNull LocalDate dueDate,
    OffsetDateTime returnedAt,
    long finePaise,
    String note
) {
    public static IssueDto from(BookIssue i) {
        return new IssueDto(i.getId(), i.getBookId(), i.getStudentId(),
            i.getIssuedAt(), i.getDueDate(), i.getReturnedAt(), i.getFinePaise(), i.getNote());
    }
}
