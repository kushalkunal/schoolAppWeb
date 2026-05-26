package in.schoolapp.library.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "library_issues")
@Getter
@Setter
public class BookIssue extends BaseEntity {

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "fine_paise", nullable = false)
    private long finePaise;

    @Column(name = "issued_by_id")
    private UUID issuedById;

    @Column(columnDefinition = "TEXT")
    private String note;
}
