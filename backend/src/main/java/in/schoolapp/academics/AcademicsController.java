package in.schoolapp.academics;

import in.schoolapp.academics.dto.BulkMarksRequest;
import in.schoolapp.academics.dto.CreateExamRequest;
import in.schoolapp.academics.dto.CreateSubjectsRequest;
import in.schoolapp.academics.dto.ExamCompletionStatusResponse;
import in.schoolapp.academics.dto.ExamEligibilityResponse;
import in.schoolapp.academics.dto.ExamResponse;
import in.schoolapp.academics.dto.MarkResponse;
import in.schoolapp.academics.dto.MarksEntrySheetResponse;
import in.schoolapp.academics.dto.ReportCardResponse;
import in.schoolapp.academics.dto.SubjectResponse;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.ACADEMICS)
public class AcademicsController {

    private final SubjectService subjectService;
    private final ExamService examService;
    private final MarksService marksService;
    private final ReportCardService reportCardService;
    private final ExamEligibilityService examEligibilityService;

    // ----- Subjects -----

    @PostMapping("/subjects/bulk")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> bulkCreateSubjects(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateSubjectsRequest request
    ) {
        List<SubjectResponse> created = subjectService.bulkCreate(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/subjects")
    public ApiResponse<List<SubjectResponse>> listSubjects(@PathVariable UUID tenantId) {
        return ApiResponse.success(subjectService.listSubjects(tenantId));
    }

    // ----- Exams -----

    @PostMapping("/exams")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<ExamResponse>> createExam(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateExamRequest request
    ) {
        ExamResponse created = examService.createExam(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/exams")
    public ApiResponse<List<ExamResponse>> listExams(@PathVariable UUID tenantId) {
        return ApiResponse.success(examService.listCurrentYearExams(tenantId));
    }

    @PostMapping("/exams/{examId}/publish")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<ExamResponse> publishExam(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId
    ) {
        return ApiResponse.success(examService.publishExam(tenantId, examId));
    }

    // ----- Marks -----

    @GetMapping("/exams/{examId}/marks/{sectionId}")
    public ApiResponse<MarksEntrySheetResponse> getMarksEntrySheet(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(marksService.getEntrySheet(tenantId, examId, sectionId));
    }

    @PostMapping("/exams/{examId}/marks")
    @PreAuthorize(AppRoles.MARKS_WRITER)
    public ResponseEntity<ApiResponse<List<MarkResponse>>> submitMarks(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @Valid @RequestBody BulkMarksRequest request
    ) {
        List<MarkResponse> saved = marksService.submitBulk(tenantId, examId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved));
    }

    @GetMapping("/exams/{examId}/completion/{sectionId}")
    public ApiResponse<ExamCompletionStatusResponse> getCompletionStatus(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(marksService.getCompletionStatus(tenantId, examId, sectionId));
    }

    // ----- Report cards -----

    @PostMapping("/exams/{examId}/report-cards/generate/{sectionId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<List<ReportCardResponse>>> generateReportCards(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        List<ReportCardResponse> cards = reportCardService.generateForSection(tenantId, examId, sectionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(cards));
    }

    @GetMapping("/students/{studentId}/report-card/{examId}")
    public ApiResponse<ReportCardResponse> getStudentReportCard(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @PathVariable UUID examId
    ) {
        return ApiResponse.success(reportCardService.getReportCard(tenantId, studentId, examId));
    }

    // ----- Exam eligibility (attendance-% based) -----

    @GetMapping("/sections/{sectionId}/exam-eligibility")
    public ApiResponse<ExamEligibilityResponse> getExamEligibility(
        @PathVariable UUID tenantId,
        @PathVariable UUID sectionId,
        @RequestParam(required = false) Integer windowDays
    ) {
        return ApiResponse.success(examEligibilityService.forSection(tenantId, sectionId, windowDays));
    }
}
