import { apiClient, apiGet, apiPost, apiDelete } from '@/api/client';
import type { Meta } from '@/types/api';
import type {
  CreateStudentRequest,
  StudentProfileResponse,
  StudentResponse,
} from '@/types/domain';

export const studentsApi = {
  /**
   * Server returns the envelope `{ success, data, meta }`. We need both `data` and `meta`
   * for paginated list views — apiGet only exposes `data`, so we call axios directly.
   */
  async list(tenantId: string, params?: { search?: string; page?: number; size?: number })
  : Promise<{ items: StudentResponse[]; meta: Meta }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/students`, { params });
    return { items: res.data.data as StudentResponse[], meta: (res.data.meta ?? {}) as Meta };
  },

  get(tenantId: string, studentId: string): Promise<StudentProfileResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/students/${studentId}`);
  },

  /** Slice 36 — active roster for one section. Backs the attendance grid's first-mark flow. */
  bySection(tenantId: string, sectionId: string): Promise<StudentResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/students/by-section/${sectionId}`);
  },

  create(tenantId: string, req: CreateStudentRequest): Promise<StudentResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/students`, req);
  },

  deactivate(tenantId: string, studentId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/students/${studentId}`);
  },
};
