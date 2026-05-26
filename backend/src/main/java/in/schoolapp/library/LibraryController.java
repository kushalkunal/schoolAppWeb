package in.schoolapp.library;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.library.dto.BookDto;
import in.schoolapp.library.dto.IssueDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService service;

    @PostMapping("/books")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<BookDto>> createBook(@PathVariable UUID tenantId,
                                                            @Valid @RequestBody BookDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createBook(tenantId, req)));
    }

    @GetMapping("/books")
    public ApiResponse<List<BookDto>> listBooks(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listBooks(tenantId));
    }

    @DeleteMapping("/books/{bookId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> deactivateBook(@PathVariable UUID tenantId, @PathVariable UUID bookId) {
        service.deactivateBook(tenantId, bookId);
        return ApiResponse.ok();
    }

    @PostMapping("/issues")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<IssueDto>> issue(@PathVariable UUID tenantId,
                                                       @RequestParam UUID bookId,
                                                       @RequestParam UUID studentId,
                                                       @RequestParam(required = false) LocalDate dueDate,
                                                       @RequestParam(required = false) String note) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.issue(tenantId, bookId, studentId, dueDate, note)));
    }

    @PostMapping("/issues/{issueId}/return")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<IssueDto> returnBook(@PathVariable UUID tenantId, @PathVariable UUID issueId) {
        return ApiResponse.success(service.returnBook(tenantId, issueId));
    }

    @GetMapping("/issues/outstanding")
    public ApiResponse<List<IssueDto>> outstanding(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listOutstanding(tenantId));
    }

    @GetMapping("/students/{studentId}/issues")
    public ApiResponse<List<IssueDto>> forStudent(@PathVariable UUID tenantId,
                                                  @PathVariable UUID studentId) {
        return ApiResponse.success(service.listForStudent(tenantId, studentId));
    }
}
