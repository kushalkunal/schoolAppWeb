import { apiGet, apiPost } from '@/api/client';
import type { StudentRiskScore } from '@/types/domain';

/**
 * Reads + manually-triggers at-risk student scoring.
 * Server-side cron refreshes nightly; admins can also kick a recompute via POST /recompute.
 */
export const riskApi = {
  list(tenantId: string, params?: { minScore?: number; page?: number; size?: number })
  : Promise<StudentRiskScore[]> {
    const q = new URLSearchParams();
    if (params?.minScore != null) q.set('minScore', String(params.minScore));
    if (params?.page != null)     q.set('page',     String(params.page));
    if (params?.size != null)     q.set('size',     String(params.size));
    const qs = q.toString() ? `?${q}` : '';
    return apiGet(`/api/v1/tenants/${tenantId}/risk/students${qs}`);
  },

  getOne(tenantId: string, studentId: string): Promise<StudentRiskScore> {
    return apiGet(`/api/v1/tenants/${tenantId}/risk/students/${studentId}`);
  },

  recompute(tenantId: string): Promise<{ scored: number; alerts: number }> {
    return apiPost(`/api/v1/tenants/${tenantId}/risk/recompute`);
  },
};
