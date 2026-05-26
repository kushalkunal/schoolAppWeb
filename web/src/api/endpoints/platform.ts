import { apiClient, apiDelete, apiGet, apiPost, apiPut } from '@/api/client';
import type { Meta } from '@/types/api';
import type {
  Feature,
  Plan,
  PlatformTenantDetail,
  PlatformTenantSummary,
  TenantProviderConfig,
} from '@/types/domain';

export interface ChangePlanRequest { planCode: string; note?: string }
export interface SuspendRequest { reason: string }
export interface FeatureOverrideRequest { enabled: boolean; note?: string }
export interface SetProviderConfigRequest {
  provider: string;
  config: Record<string, unknown>;
  note?: string;
}

export const platformApi = {
  async listTenants(params?: { page?: number; size?: number })
  : Promise<{ items: PlatformTenantSummary[]; meta: Meta }> {
    const res = await apiClient.get('/api/v1/platform/tenants', { params });
    return { items: res.data.data as PlatformTenantSummary[], meta: (res.data.meta ?? {}) as Meta };
  },

  getTenant(schoolId: string): Promise<PlatformTenantDetail> {
    return apiGet(`/api/v1/platform/tenants/${schoolId}`);
  },

  changePlan(schoolId: string, req: ChangePlanRequest) {
    return apiPut(`/api/v1/platform/tenants/${schoolId}/subscription`, req);
  },

  suspend(schoolId: string, req: SuspendRequest) {
    return apiPost(`/api/v1/platform/tenants/${schoolId}/suspend`, req);
  },

  resume(schoolId: string) {
    return apiPost(`/api/v1/platform/tenants/${schoolId}/resume`);
  },

  setFeatureOverride(schoolId: string, featureKey: string, req: FeatureOverrideRequest) {
    return apiPut(`/api/v1/platform/tenants/${schoolId}/features/${featureKey}`, req);
  },

  clearFeatureOverride(schoolId: string, featureKey: string) {
    return apiDelete(`/api/v1/platform/tenants/${schoolId}/features/${featureKey}`);
  },

  listPlans(): Promise<Plan[]> {
    return apiGet('/api/v1/platform/plans');
  },

  listFeatures(): Promise<Feature[]> {
    return apiGet('/api/v1/platform/features');
  },

  listProviders(schoolId: string): Promise<TenantProviderConfig[]> {
    return apiGet(`/api/v1/platform/tenants/${schoolId}/providers`);
  },

  setProvider(schoolId: string, concern: string, req: SetProviderConfigRequest) {
    return apiPut(`/api/v1/platform/tenants/${schoolId}/providers/${concern}`, req);
  },

  clearProvider(schoolId: string, concern: string) {
    return apiDelete(`/api/v1/platform/tenants/${schoolId}/providers/${concern}`);
  },

  verifyProvider(schoolId: string, concern: string) {
    return apiPost(`/api/v1/platform/tenants/${schoolId}/providers/${concern}/verify`);
  },
};
