import { apiGet } from '@/api/client';

export interface AllocPerson {
  staffId: string;
  name: string;
  role: string;
}
export interface AllocSubject {
  subjectId: string;
  subjectName: string;
  teacher: AllocPerson | null;
}
export interface AllocSection {
  sectionId: string;
  sectionName: string;
  classTeacher: AllocPerson | null;
  subjects: AllocSubject[];
}
export interface AllocClass {
  classId: string;
  className: string;
  sections: AllocSection[];
}
export interface TeacherWorkload {
  staffId: string;
  name: string;
  role: string;
  classTeacherOf: string[];
  subjectCount: number;
  sectionCount: number;
  periodsPerWeek: number;
  freePeriods: number;
  weeklyCapacity: number;
  utilizationPct: number;
}
export interface OngoingClass {
  sectionLabel: string;
  subjectName: string;
  teacherName: string;
}
export interface NowTeaching {
  inSession: boolean;
  currentPeriodName: string | null;
  startTime: string | null;
  endTime: string | null;
  ongoing: OngoingClass[];
}
export interface AllocationSummary {
  totalClasses: number;
  totalSections: number;
  totalTeachers: number;
  sectionsWithoutClassTeacher: number;
  sectionsWithoutSubjects: number;
  teachersWithoutLoad: number;
  totalSubjectAssignments: number;
  workingDays: number;
  periodsPerDay: number;
}
export interface AllocationOverview {
  summary: AllocationSummary;
  classes: AllocClass[];
  teachers: TeacherWorkload[];
  now: NowTeaching;
}

export const teacherAllocationApi = {
  /** Single centralized overview for the Allocation Command Center (admin only). */
  overview(tenantId: string): Promise<AllocationOverview> {
    return apiGet(`/api/v1/tenants/${tenantId}/teacher-allocation/overview`);
  },
};
