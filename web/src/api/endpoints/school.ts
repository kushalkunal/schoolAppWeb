import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '@/api/client';
import type {
  AssignClassTeacherRequest,
  ClassResponse,
  CreateClassesRequest,
  CreateStaffRequest,
  InviteTeacherRequest,
  OnboardingStatusResponse,
  SchoolResponse,
  SectionResponse,
  StaffResponse,
  UpdateStaffRequest,
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
  /**
   * Assign a CLASS_TEACHER as the class teacher of a section.
   * ADMIN / PRINCIPAL / OWNER only.
   */
  assignClassTeacher(
    tenantId: string,
    sectionId: string,
    req: AssignClassTeacherRequest,
  ): Promise<SectionResponse> {
    return apiPatch(`/api/v1/tenants/${tenantId}/sections/${sectionId}/class-teacher`, req);
  },
  /**
   * Returns the sections where the current authenticated staff member is the class teacher.
   * Used by CLASS_TEACHER role to see only their assigned sections.
   */
  myAssignedSections(tenantId: string): Promise<SectionResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/sections/mine`);
  },

  // Staff
  listStaff(tenantId: string): Promise<StaffResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/staff`);
  },
  getStaff(tenantId: string, staffId: string): Promise<StaffResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/staff/${staffId}`);
  },
  updateStaff(tenantId: string, staffId: string, req: UpdateStaffRequest): Promise<StaffResponse> {
    return apiPatch(`/api/v1/tenants/${tenantId}/staff/${staffId}`, req);
  },
  createStaff(tenantId: string, req: CreateStaffRequest): Promise<StaffResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/staff`, req);
  },
  deactivateStaff(tenantId: string, staffId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/staff/${staffId}`);
  },
  inviteTeacher(tenantId: string, req: InviteTeacherRequest): Promise<StaffResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/staff/invite-teacher`, req);
  },
};
