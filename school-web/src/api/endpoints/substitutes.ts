import { apiDelete, apiGet, apiPost } from '@/api/client';
import type { CreateSubstituteRequest, SubstituteResponse } from '@/types/domain';

export const substitutesApi = {
  assign(tenantId: string, req: CreateSubstituteRequest): Promise<SubstituteResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/substitutes`, req);
  },
  listForDate(tenantId: string, date: string): Promise<SubstituteResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/substitutes`, { date });
  },
  cancel(tenantId: string, assignmentId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/substitutes/${assignmentId}`);
  },
};
