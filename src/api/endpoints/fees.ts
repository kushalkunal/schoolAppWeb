import { apiClient, apiGet, apiPost } from '@/api/client';
import type { Meta } from '@/types/api';
import type {
  ClassCollectionRow,
  DefaulterResponse,
  FeeDashboardResponse,
  PaymentResponse,
  QuickCollectRequest,
  RecentPaymentRow,
  StudentFeeSummaryResponse,
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

  studentSummary(tenantId: string, studentId: string): Promise<StudentFeeSummaryResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/students/${studentId}/fee-summary`);
  },

  recentPayments(tenantId: string, size = 20): Promise<RecentPaymentRow[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/fees/payments/recent?size=${size}`);
  },

  classWiseReport(tenantId: string, from?: string, to?: string): Promise<ClassCollectionRow[]> {
    const qs = new URLSearchParams();
    if (from) qs.set('from', from);
    if (to) qs.set('to', to);
    return apiGet(`/api/v1/tenants/${tenantId}/fees/reports/class-wise?${qs}`);
  },
};
