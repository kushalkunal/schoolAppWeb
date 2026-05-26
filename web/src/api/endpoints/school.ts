import { apiDelete, apiGet, apiPost, apiPut } from '@/api/client';
import type {
  ClassResponse,
  CreateClassesRequest,
  CreateStaffRequest,
  OnboardingStatusResponse,
  SchoolResponse,
  StaffResponse,
  UpdateSchoolRequest,
} from '@/types/domain';

export const schoolApi = {
  get(tenantId: string): Promise<SchoolResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}`);
  },
  update(tenantId: string, req: UpdateSchoolRequest): Promise<SchoolResponse> {
    return apiPut(`/api/v1/tenants/${tenantId}`, req);
  },
  onboardingStatus(tenantId: string): Promise<OnboardingStatusResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/onboarding-status`);
  },

  // Classes + sections
  listClasses(tenantId: string): Promise<ClassResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/classes`);
  },
  createClasses(tenantId: string, req: CreateClassesRequest): Promise<ClassResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/classes/bulk`, req);
  },

  // Staff
  listStaff(tenantId: string): Promise<StaffResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/staff`);
  },
  createStaff(tenantId: string, req: CreateStaffRequest): Promise<StaffResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/staff`, req);
  },
  deactivateStaff(tenantId: string, staffId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/staff/${staffId}`);
  },
};
