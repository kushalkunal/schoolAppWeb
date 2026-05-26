import { apiClient, apiDelete, apiGet, apiPost } from '@/api/client';

export interface AssignmentDto {
  id: string;
  sectionId: string;
  subjectId: string | null;
  title: string;
  body: string;
  attachmentUrl: string | null;
  dueDate: string | null;
  createdAt: string;
}

export interface SubmissionDto {
  id: string;
  assignmentId: string;
  studentId: string;
  submissionText: string | null;
  attachmentUrl: string | null;
  teacherRemark: string | null;
  grade: string | null;
  submittedAt: string;
  gradedAt: string | null;
}

export interface CreateAssignmentRequest {
  sectionId: string;
  subjectId?: string;
  title: string;
  body: string;
  attachmentUrl?: string;
  dueDate?: string;
}

export const homeworkApi = {
  listAssignments(tenantId: string, sectionId?: string): Promise<AssignmentDto[]> {
    const qs = sectionId ? `?sectionId=${sectionId}` : '';
    return apiGet(`/api/v1/tenants/${tenantId}/homework/assignments${qs}`);
  },
  createAssignment(tenantId: string, req: CreateAssignmentRequest): Promise<AssignmentDto> {
    return apiPost(`/api/v1/tenants/${tenantId}/homework/assignments`, req);
  },
  deleteAssignment(tenantId: string, assignmentId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/homework/assignments/${assignmentId}`);
  },
  listSubmissions(tenantId: string, assignmentId: string): Promise<SubmissionDto[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/homework/assignments/${assignmentId}/submissions`);
  },
  grade(tenantId: string, submissionId: string, grade: string, remark?: string)
  : Promise<SubmissionDto> {
    const q = new URLSearchParams({ grade });
    if (remark) q.set('remark', remark);
    return apiClient.post(`/api/v1/tenants/${tenantId}/homework/submissions/${submissionId}/grade?${q}`)
      .then((r) => r.data.data as SubmissionDto);
  },
};
