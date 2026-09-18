import { apiGet, apiPost } from '@/api/client';
import type {
  AttendanceRecordResponse,
  AttendanceSectionResponse,
  AttendanceSubmitResponse,
  AttendanceSummaryResponse,
  StaffAttendanceResponse,
  StaffAttendanceStatus,
  SubmitAttendanceRequest,
  UnmarkedSectionResponse,
} from '@/types/domain';

export const attendanceApi = {
  submit(tenantId: string, sectionId: string, req: SubmitAttendanceRequest)
  : Promise<AttendanceSubmitResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/sections/${sectionId}/attendance`, req);
  },

  getSection(tenantId: string, sectionId: string, date: string)
  : Promise<AttendanceSectionResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/sections/${sectionId}/attendance`, { date });
  },

  summary(tenantId: string, date: string): Promise<AttendanceSummaryResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/attendance/summary`, { date });
  },

  unmarked(tenantId: string, date: string): Promise<UnmarkedSectionResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/attendance/unmarked`, { date });
  },

  // ---- Staff self-attendance ----

  /** Teacher marks their own attendance for today. */
  markSelf(tenantId: string, status: StaffAttendanceStatus, notes?: string)
  : Promise<StaffAttendanceResponse> {
    const params: Record<string, string> = { status };
    if (notes) params.notes = notes;
    return apiPost(`/api/v1/tenants/${tenantId}/hr/attendance/self?status=${status}${notes ? `&notes=${encodeURIComponent(notes)}` : ''}`, {});
  },

  /** Teacher views their own attendance history. */
  myAttendance(tenantId: string, from?: string, to?: string): Promise<StaffAttendanceResponse[]> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    return apiGet(`/api/v1/tenants/${tenantId}/hr/attendance/me`, params);
  },

  /** Admin: list pending approval staff attendance entries. */
  staffPendingApprovals(tenantId: string, date?: string): Promise<StaffAttendanceResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hr/attendance/pending-approvals`,
      date ? { date } : {});
  },

  /** Admin: approve a staff member's self-submitted attendance. */
  approveStaff(tenantId: string, staffId: string, date?: string): Promise<StaffAttendanceResponse> {
    const qs = date ? `?date=${date}` : '';
    return apiPost(`/api/v1/tenants/${tenantId}/hr/attendance/${staffId}/approve${qs}`, {});
  },

  /** Admin/Principal: full attendance history for one student within a date range. */
  getStudentAttendance(
    tenantId: string, studentId: string, from: string, to: string,
  ): Promise<AttendanceRecordResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/students/${studentId}/attendance`, { from, to });
  },
};
