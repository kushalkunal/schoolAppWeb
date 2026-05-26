import { apiGet, apiPost } from '@/api/client';
import type {
  AttendanceRecordResponse,
  AttendanceSubmitResponse,
  AttendanceSummaryResponse,
  SubmitAttendanceRequest,
  UnmarkedSectionResponse,
} from '@/types/domain';

export const attendanceApi = {
  submit(tenantId: string, sectionId: string, req: SubmitAttendanceRequest)
  : Promise<AttendanceSubmitResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/sections/${sectionId}/attendance`, req);
  },

  getSection(tenantId: string, sectionId: string, date: string)
  : Promise<AttendanceRecordResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/sections/${sectionId}/attendance`, { date });
  },

  summary(tenantId: string, date: string): Promise<AttendanceSummaryResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/attendance/summary`, { date });
  },

  unmarked(tenantId: string, date: string): Promise<UnmarkedSectionResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/attendance/unmarked`, { date });
  },
};
