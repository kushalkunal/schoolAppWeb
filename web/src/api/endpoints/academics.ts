import { apiGet, apiPost } from '@/api/client';
import type {
  BulkMarksRequest,
  CreateExamRequest,
  CreateSubjectsRequest,
  ExamCompletionStatusResponse,
  ExamResponse,
  MarkResponse,
  MarksEntrySheetResponse,
  ReportCardResponse,
  SubjectResponse,
} from '@/types/domain';

/**
 * Wrappers for `/api/v1/tenants/{tenantId}/...` academics endpoints (see
 * `backend/src/main/java/in/schoolapp/academics/AcademicsController.java`).
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

  // Marks
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

  // Report cards
  generateReportCards(tenantId: string, examId: string, sectionId: string): Promise<ReportCardResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/exams/${examId}/report-cards/generate/${sectionId}`);
  },
  getStudentReportCard(tenantId: string, studentId: string, examId: string): Promise<ReportCardResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/students/${studentId}/report-card/${examId}`);
  },
};
