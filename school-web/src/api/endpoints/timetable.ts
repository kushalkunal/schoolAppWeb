import { apiDelete, apiGet, apiPost } from '@/api/client';
import type {
  AssignSubstitutionRequest,
  CreatePeriodRequest,
  PeriodResponse,
  SubstitutionResponse,
  TimetableEntryResponse,
  UpsertTimetableEntryRequest,
} from '@/types/domain';

export interface SubstituteCandidate {
  staffId: string;
  name: string;
  role: string;
  note: string;
}
export interface SubstituteCandidatesResponse {
  absent: SubstituteCandidate[];
  available: SubstituteCandidate[];
  busy: SubstituteCandidate[];
}

export const timetableApi = {
  listPeriods(tenantId: string): Promise<PeriodResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/timetable/periods`);
  },
  createPeriod(tenantId: string, req: CreatePeriodRequest): Promise<PeriodResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/timetable/periods`, req);
  },
  deletePeriod(tenantId: string, periodId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/timetable/periods/${periodId}`);
  },
  getSectionTimetable(tenantId: string, sectionId: string): Promise<TimetableEntryResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/timetable/sections/${sectionId}`);
  },
  getTeacherTimetable(tenantId: string, teacherId: string): Promise<TimetableEntryResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/timetable/teachers/${teacherId}`);
  },
  /** Returns the logged-in teacher's classes for today, including any substitution slots. */
  getTodayForMe(tenantId: string): Promise<TimetableEntryResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/timetable/today/me`);
  },
  upsertEntry(tenantId: string, req: UpsertTimetableEntryRequest): Promise<TimetableEntryResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/timetable/entries`, req);
  },
  deleteEntry(tenantId: string, entryId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/timetable/entries/${entryId}`);
  },
  /** Assign a substitute teacher for a section × period × date. */
  assignSubstitution(tenantId: string, req: AssignSubstitutionRequest): Promise<SubstitutionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/timetable/substitutions`, req);
  },
  /** List all substitutions for a given date (admin view). Defaults to today. */
  listSubstitutions(tenantId: string, date?: string): Promise<SubstitutionResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/timetable/substitutions`, date ? { date } : {});
  },
  /**
   * Availability-aware substitute picker for one period on one date: teachers split into
   * absent-today / free-at-this-slot / busy-at-this-slot.
   */
  getSubstituteCandidates(
    tenantId: string,
    params: { periodId: string; date?: string; sectionId?: string },
  ): Promise<SubstituteCandidatesResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/timetable/substitute-candidates`, params);
  },
};
