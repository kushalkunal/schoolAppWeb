package in.schoolapp.student;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.student.dto.CreateStudentRequest;
import in.schoolapp.student.dto.StudentDocumentResponse;
import in.schoolapp.student.dto.StudentProfileResponse;
import in.schoolapp.student.dto.StudentResponse;
import in.schoolapp.student.dto.StudentTimelineResponse;
import in.schoolapp.student.dto.UpdateStudentRequest;
import in.schoolapp.student.entity.StudentDocumentType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final FamilyService familyService;
    private final StudentTimelineService timelineService;

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<StudentResponse>> createStudent(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateStudentRequest request
    ) {
        StudentResponse response = studentService.createStudent(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<StudentResponse>> listStudents(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Page<StudentResponse> p = studentService.listStudents(tenantId, search, page, size);
        return ApiResponse.success(
            p.getContent(),
            new ApiResponse.Meta(p.getTotalElements(), p.getNumber(), p.getSize(), null)
        );
    }

    /**
     * Slice 36 — section roster. Returns the active students in a section so the attendance
     * marking screen can seed the grid even when no attendance has been recorded yet (fixes
     * the "use the mobile app" dead-end the audit flagged).
     */
    @GetMapping("/by-section/{sectionId}")
    public ApiResponse<List<StudentResponse>> listBySection(
        @PathVariable UUID tenantId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(studentService.listBySection(tenantId, sectionId));
    }

    @GetMapping("/{studentId}")
    public ApiResponse<StudentProfileResponse> getStudent(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId
    ) {
        return ApiResponse.success(familyService.getProfile(tenantId, studentId));
    }

    @PutMapping("/{studentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<StudentResponse> updateStudent(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @Valid @RequestBody UpdateStudentRequest request
    ) {
        return ApiResponse.success(studentService.updateStudent(tenantId, studentId, request));
    }

    @DeleteMapping("/{studentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> deactivateStudent(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId
    ) {
        studentService.deactivateStudent(tenantId, studentId);
        return ApiResponse.ok();
    }

    @GetMapping("/{studentId}/timeline")
    public ApiResponse<StudentTimelineResponse> getTimeline(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(timelineService.get(tenantId, studentId));
    }

    @PostMapping(value = "/{studentId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<StudentResponse> uploadPhoto(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Photo file is empty");
        }
        try {
            return ApiResponse.success(studentService.uploadPhoto(
                tenantId, studentId, file.getBytes(), file.getContentType()));
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Could not read uploaded file");
        }
    }

    @PostMapping(value = "/{studentId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<StudentDocumentResponse>> uploadDocument(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @RequestParam("type") StudentDocumentType type,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Document file is empty");
        }
        try {
            StudentDocumentResponse response = studentService.uploadDocument(
                tenantId, studentId, type, file.getBytes(),
                file.getContentType(), file.getOriginalFilename());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Could not read uploaded file");
        }
    }

    @GetMapping("/{studentId}/documents")
    public ApiResponse<List<StudentDocumentResponse>> listDocuments(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(studentService.listDocuments(tenantId, studentId));
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> deleteDocument(
        @PathVariable UUID tenantId, @PathVariable UUID documentId
    ) {
        studentService.deleteDocument(tenantId, documentId);
        return ApiResponse.ok();
    }
}
