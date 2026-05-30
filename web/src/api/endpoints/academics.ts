import { apiGet, apiPost } from '@/api/client';
import type {
  AdmitCardDashboardResponse,
  AdmitCardResponse,
  BulkComponentMarksRequest,
  BulkMarksRequest,
  ComponentMarksSheetResponse,
  ConfigureExamStructureRequest,
  CreateExamRequest,
  CreateSubjectsRequest,
  ExamCompletionStatusResponse,
  ExamResponse,
  ExamResultResponse,
  ExamStructureResponse,
  MarkResponse,
  MarksEntrySheetResponse,
  ReportCardResponse,
  ResultDashboardResponse,
  SubjectResponse,
} from '@/types/domain';

/**
 * Wrappers for `/api/v1/tenants/{tenantId}/...` academics endpoints (see
 * `backend/src/main/java/in/schoolapp/academics/AcademicsController.java` and
 * `ExamResultController.java`).
 *
 * Note: the controller is gated by `@RequiresFeature(ACADEMICS)`. Plans below ESSENTIALS
 * will get `FEATURE_DISABLED` — the standard 402-style upgrade prompt.
 */
export const academicsApi = {
  // Subjects
  listSubjects(tenantId: string): Promise<SubjectResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/subjects`);
  },
  bulkCreateSubjects(tenantId: string, req: CreateSubjectsRequest): Promise<SubjectResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/subjects/bulk`, req);
  },

  // Exams
  listExams(tenantId: string): Promise<ExamResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams`);
  },
  createExam(tenantId: string, req: CreateExamRequest): Promise<ExamResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams`, req);
  },
  publishExam(tenantId: string, examId: string): Promise<ExamResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/publish`);
  },

  // Exam structure (marking scheme)
  getExamStructure(tenantId: string, examId: string): Promise<ExamStructureResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/structure`);
  },
  configureExamStructure(
    tenantId: string, examId: string, req: ConfigureExamStructureRequest
  ): Promise<ExamStructureResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/structure`, req);
  },

  // Flat marks (legacy path — still used when no structure is configured)
  getMarksSheet(tenantId: string, examId: string, sectionId: string): Promise<MarksEntrySheetResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/marks/${sectionId}`);
  },
  submitMarks(tenantId: string, examId: string, req: BulkMarksRequest): Promise<MarkResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/marks`, req);
  },
  getCompletionStatus(tenantId: string, examId: string, sectionId: string)
  : Promise<ExamCompletionStatusResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/completion/${sectionId}`);
  },

  // Component marks entry (structure-aware path)
  getComponentMarksSheet(
    tenantId: string, examId: string, sectionId: string
  ): Promise<ComponentMarksSheetResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/component-marks/${sectionId}`);
  },
  submitComponentMarks(
    tenantId: string, examId: string, req: BulkComponentMarksRequest
  ): Promise<void> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/component-marks`, req);
  },

  // Results
  computeResults(tenantId: string, examId: string, sectionId: string): Promise<ExamResultResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/results/compute/${sectionId}`);
  },
  getResults(tenantId: string, examId: string, sectionId: string): Promise<ExamResultResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/results/${sectionId}`);
  },
  publishResults(tenantId: string, examId: string, sectionId: string): Promise<ExamResultResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/results/publish/${sectionId}`);
  },
  getResultDashboard(tenantId: string, examId: string): Promise<ResultDashboardResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/dashboard`);
  },

  // Report cards
  generateReportCards(tenantId: string, examId: string, sectionId: string): Promise<ReportCardResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/report-cards/generate/${sectionId}`);
  },
  getStudentReportCard(tenantId: string, studentId: string, examId: string): Promise<ReportCardResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/students/${studentId}/report-card/${examId}`);
  },

  // Admit Cards
  getAdmitCardStats(tenantId: string, examId: string): Promise<AdmitCardDashboardResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/admit-cards/stats`);
  },
  listAdmitCards(tenantId: string, examId: string, status?: string): Promise<AdmitCardResponse[]> {
    const q = status ? `?status=${status}` : '';
    return apiGet(`/api/v1/tenants/${tenantId}/exams/${examId}/admit-cards${q}`);
  },
  bulkGenerateAdmitCards(tenantId: string, examId: string): Promise<{ generated: number }> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/admit-cards/generate`, {});
  },
  regenerateAdmitCard(tenantId: string, examId: string, studentId: string): Promise<AdmitCardResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/admit-cards/${studentId}/regenerate`, {});
  },
  markAdmitCardDownloaded(tenantId: string, examId: string, admitCardId: string): Promise<AdmitCardResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/admit-cards/${admitCardId}/download`, {});
  },
};
