import { apiGet, apiPost } from '@/api/client';

export interface AbsentTeacher { staffId: string; name: string; reason: string; }
export interface SubCandidate {
  staffId: string; name: string; role: string;
  sameSubject: boolean; teachesClass: boolean; periodsPerWeek: number; recommended: boolean;
}
export interface PeriodPlan {
  periodId: string; periodName: string; startTime: string | null; endTime: string | null;
  sectionId: string; sectionLabel: string; subjectId: string | null; subjectName: string;
  recommendedStaffId: string | null; alreadyCovered: boolean; candidates: SubCandidate[];
}
export interface ReplacementPlan {
  absentTeacherId: string; absentTeacherName: string;
  isClassTeacher: boolean; classTeacherOf: string[]; periods: PeriodPlan[];
}
export interface AutoAssignResult { assigned: number; skipped: number; messages: string[]; }
export interface SubDashboard {
  absentTeachers: number; substitutionsAssigned: number; pendingSubstitutions: number; classesWithoutTeacher: number;
}

export interface MyTodayClass {
  sectionId: string; sectionLabel: string; subjectName: string;
  periodName: string; startTime: string | null; endTime: string | null; attendanceSubmitted: boolean;
}
export interface HistoryRow {
  date: string; substituteName: string; absentTeacherName: string;
  sectionLabel: string; subjectName: string; periodName: string; attendanceMarkedBy: string;
}

const q = (date?: string) => (date ? { date } : {});

export const substitutionApi = {
  absentToday(tenantId: string, date?: string): Promise<AbsentTeacher[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/substitution/absent-today`, q(date));
  },
  plan(tenantId: string, teacherId: string, date?: string): Promise<ReplacementPlan> {
    return apiGet(`/api/v1/tenants/${tenantId}/substitution/plan`, { teacherId, ...q(date) });
  },
  autoAssign(tenantId: string, teacherId: string, date?: string): Promise<AutoAssignResult> {
    return apiPost(`/api/v1/tenants/${tenantId}/substitution/auto-assign?teacherId=${teacherId}${date ? `&date=${date}` : ''}`);
  },
  dashboard(tenantId: string, date?: string): Promise<SubDashboard> {
    return apiGet(`/api/v1/tenants/${tenantId}/substitution/dashboard`, q(date));
  },
  myToday(tenantId: string): Promise<MyTodayClass[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/substitution/my-today`);
  },
  history(tenantId: string, from?: string, to?: string): Promise<HistoryRow[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/substitution/history`, { ...(from ? { from } : {}), ...(to ? { to } : {}) });
  },
};
