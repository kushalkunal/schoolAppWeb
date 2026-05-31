import { apiGet, apiPost, apiPut, apiDelete } from '@/api/client';

export interface Holiday {
  id: string;
  date: string;       // ISO date
  name: string;
  type: string;       // HOLIDAY | EVENT | EXAM | VACATION
}
export interface CalendarData {
  workingDays: number[];   // ISO day numbers 1=Mon … 7=Sun
  holidays: Holiday[];
}

export const calendarApi = {
  get(tenantId: string): Promise<CalendarData> {
    return apiGet(`/api/v1/tenants/${tenantId}/calendar`);
  },
  setWorkingDays(tenantId: string, days: number[]): Promise<CalendarData> {
    return apiPut(`/api/v1/tenants/${tenantId}/calendar/working-days`, { days });
  },
  addHoliday(tenantId: string, body: { date: string; name: string; type: string }): Promise<Holiday> {
    return apiPost(`/api/v1/tenants/${tenantId}/calendar/holidays`, body);
  },
  deleteHoliday(tenantId: string, id: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/calendar/holidays/${id}`);
  },
};
