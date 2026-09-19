import { apiDelete, apiGet, apiPost } from '@/api/client';
import type { CreateTeacherAssignmentRequest, TeacherAssignmentResponse } from '@/types/domain';

export const teacherAssignmentsApi = {
  list(tenantId: string, staffId?: string): Promise<TeacherAssignmentResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/teacher-assignments`, staffId ? { staffId } : undefined);
  },
  assign(tenantId: string, req: CreateTeacherAssignmentRequest): Promise<TeacherAssignmentResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/teacher-assignments`, req);
  },
  unassign(tenantId: string, id: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/teacher-assignments/${id}`);
  },
};
