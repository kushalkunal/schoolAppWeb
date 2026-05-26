import { apiClient, apiGet, apiPost } from '@/api/client';
import type { Meta } from '@/types/api';
import type { CircularResponse, CreateCircularRequest } from '@/types/domain';

export const circularsApi = {
  async list(tenantId: string, params?: { page?: number; size?: number })
  : Promise<{ items: CircularResponse[]; meta: Meta }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/circulars`, { params });
    return { items: res.data.data as CircularResponse[], meta: (res.data.meta ?? {}) as Meta };
  },

  get(tenantId: string, id: string): Promise<CircularResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/circulars/${id}`);
  },

  create(tenantId: string, req: CreateCircularRequest): Promise<CircularResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/circulars`, req);
  },
};
