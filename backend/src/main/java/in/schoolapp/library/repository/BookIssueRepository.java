package in.schoolapp.library.repository;

import in.schoolapp.library.entity.BookIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookIssueRepository extends JpaRepository<BookIssue, UUID> {

    List<BookIssue> findByStudentIdOrderByIssuedAtDesc(UUID studentId);

    List<BookIssue> findBySchoolIdAndReturnedAtIsNull(UUID schoolId);

    /** Slice 33 — overdue (not yet returned, dueDate strictly before today). */
    List<BookIssue> findBySchoolIdAndReturnedAtIsNullAndDueDateBefore(UUID schoolId, LocalDate asOf);
}
