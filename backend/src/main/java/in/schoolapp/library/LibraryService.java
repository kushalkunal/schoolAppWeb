package in.schoolapp.library;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.library.dto.BookDto;
import in.schoolapp.library.dto.IssueDto;
import in.schoolapp.library.entity.Book;
import in.schoolapp.library.entity.BookIssue;
import in.schoolapp.library.repository.BookIssueRepository;
import in.schoolapp.library.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final BookRepository bookRepository;
    private final BookIssueRepository issueRepository;

    /** Fine accrual per day overdue, in paise. ₹2/day default. */
    @Value("${app.library.fine-paise-per-day:200}")
    private long finePaisePerDay;

    // ---------- Catalog ----------

    @Transactional
    public BookDto createBook(UUID tenantId, BookDto req) {
        Book b = new Book();
        b.setSchoolId(tenantId);
        b.setTitle(req.title());
        b.setAuthor(req.author());
        b.setIsbn(req.isbn());
        b.setPublisher(req.publisher());
        b.setCategory(req.category());
        b.setTotalCopies(req.totalCopies() <= 0 ? 1 : req.totalCopies());
        b.setAvailableCopies(b.getTotalCopies());
        b.setActive(true);
        return BookDto.from(bookRepository.save(b));
    }

    @Transactional(readOnly = true)
    public List<BookDto> listBooks(UUID tenantId) {
        return bookRepository.findBySchoolIdAndActiveTrueOrderByTitleAsc(tenantId).stream()
            .map(BookDto::from).toList();
    }

    @Transactional
    public void deactivateBook(UUID tenantId, UUID bookId) {
        Book b = bookRepository.findById(bookId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Book", bookId));
        if (!b.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Book not in this school");
        }
        b.setActive(false);
        bookRepository.save(b);
    }

    // ---------- Issue / Return ----------

    @Transactional
    public IssueDto issue(UUID tenantId, UUID bookId, UUID studentId, LocalDate dueDate, String note) {
        Book b = bookRepository.findById(bookId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Book", bookId));
        if (!b.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Book not in this school");
        }
        if (b.getAvailableCopies() <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "No copies available to issue");
        }
        b.setAvailableCopies(b.getAvailableCopies() - 1);
        bookRepository.save(b);

        BookIssue issue = new BookIssue();
        issue.setSchoolId(tenantId);
        issue.setBookId(bookId);
        issue.setStudentId(studentId);
        issue.setIssuedAt(OffsetDateTime.now());
        issue.setDueDate(dueDate != null ? dueDate : LocalDate.now().plusDays(14));
        issue.setFinePaise(0);
        issue.setIssuedById(TenantContext.getStaffId());
        issue.setNote(note);
        return IssueDto.from(issueRepository.save(issue));
    }

    @Transactional
    public IssueDto returnBook(UUID tenantId, UUID issueId) {
        BookIssue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BookIssue", issueId));
        if (!issue.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Issue not in this school");
        }
        if (issue.getReturnedAt() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Book already returned");
        }
        OffsetDateTime now = OffsetDateTime.now();
        issue.setReturnedAt(now);
        // Compute overdue fine. Round-down whole days past due_date.
        LocalDate returnedDate = now.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        if (returnedDate.isAfter(issue.getDueDate())) {
            long daysLate = ChronoUnit.DAYS.between(issue.getDueDate(), returnedDate);
            issue.setFinePaise(daysLate * finePaisePerDay);
        }
        issueRepository.save(issue);

        // Return the copy to the catalog.
        Book b = bookRepository.findById(issue.getBookId()).orElseThrow();
        b.setAvailableCopies(b.getAvailableCopies() + 1);
        bookRepository.save(b);
        return IssueDto.from(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueDto> listOutstanding(UUID tenantId) {
        return issueRepository.findBySchoolIdAndReturnedAtIsNull(tenantId).stream()
            .map(IssueDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<IssueDto> listForStudent(UUID tenantId, UUID studentId) {
        return issueRepository.findByStudentIdOrderByIssuedAtDesc(studentId).stream()
            .filter(i -> i.getSchoolId().equals(tenantId))
            .map(IssueDto::from).toList();
    }
}
