import { apiGet, apiPost } from '@/api/client';
import type {
  LeaveApplicationRequest, LeaveApplicationResponse, LeaveDecisionRequest,
  PayslipResponse,
  StaffAttendanceRequest, StaffAttendanceResponse, StaffMonthlySummaryResponse,
} from '@/types/domain';

/**
 * Covers every backend endpoint under {@code /api/v1/tenants/{id}/hr/*}.
 * Three feature flags gate the underlying surface: STAFF_ATTENDANCE,
 * LEAVE_MANAGEMENT, PAYROLL — the UI surfaces FEATURE_DISABLED via the
 * existing error envelope handling.
 */
export const hrApi = {
  // ---------- Staff attendance ----------
  bulkMarkAttendance(tenantId: string, requests: StaffAttendanceRequest[])
  : Promise<StaffAttendanceResponse[]> {
    return apiPost(`/api/v1/tenants/${tenantId}/hr/attendance/mark`, requests);
  },
  listAttendance(tenantId: string, date: string): Promise<StaffAttendanceResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hr/attendance?date=${date}`);
  },
  monthlySummary(tenantId: string, staffId: string, year: number, month: number)
  : Promise<StaffMonthlySummaryResponse> {
    return apiGet(
      `/api/v1/tenants/${tenantId}/hr/attendance/${staffId}/summary?year=${year}&month=${month}`);
  },

  // ---------- Leave ----------
  submitLeave(tenantId: string, req: LeaveApplicationRequest): Promise<LeaveApplicationResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/hr/leave`, req);
  },
  decideLeave(tenantId: string, applicationId: string, req: LeaveDecisionRequest)
  : Promise<LeaveApplicationResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/hr/leave/${applicationId}/decide`, req);
  },
  cancelLeave(tenantId: string, applicationId: string): Promise<LeaveApplicationResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/hr/leave/${applicationId}/cancel`);
  },
  staffLeaves(tenantId: string, staffId: string): Promise<LeaveApplicationResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hr/leave/staff/${staffId}`);
  },
  pendingLeaves(tenantId: string): Promise<LeaveApplicationResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hr/leave/pending`);
  },

  // ---------- Payroll ----------
  generatePayslip(tenantId: string, staffId: string, year: number, month: number)
  : Promise<PayslipResponse> {
    return apiPost(
      `/api/v1/tenants/${tenantId}/hr/payslips/${staffId}/generate?year=${year}&month=${month}`);
  },
  listPayslips(tenantId: string, staffId: string): Promise<PayslipResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hr/payslips/staff/${staffId}`);
  },
};
