import { apiClient, apiGet, apiPost } from '@/api/client';
import type { Meta } from '@/types/api';
import type {
  DefaulterResponse,
  FeeDashboardResponse,
  PaymentResponse,
  QuickCollectRequest,
} from '@/types/domain';

export const feesApi = {
  dashboard(tenantId: string): Promise<FeeDashboardResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/fees/dashboard`);
  },

  quickCollect(tenantId: string, req: QuickCollectRequest): Promise<PaymentResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/fees/payments`, req);
  },

  async defaulters(tenantId: string, params?: { page?: number; size?: number })
  : Promise<{ items: DefaulterResponse[]; meta: Meta }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/fees/defaulters`, { params });
    return { items: res.data.data as DefaulterResponse[], meta: (res.data.meta ?? {}) as Meta };
  },
};
