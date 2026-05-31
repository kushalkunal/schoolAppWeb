import { apiGet, apiPost, apiPut, apiDelete } from '@/api/client';

export interface Classroom {
  id: string;
  name: string;
  code: string | null;
  building: string | null;
  capacity: number | null;
  roomType: string;   // CLASSROOM | LAB | LIBRARY | HALL | OTHER
}
export interface SaveRoom {
  name: string;
  code?: string;
  building?: string;
  capacity?: number | null;
  roomType?: string;
}

export const roomsApi = {
  list(tenantId: string): Promise<Classroom[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/classrooms`);
  },
  create(tenantId: string, body: SaveRoom): Promise<Classroom> {
    return apiPost(`/api/v1/tenants/${tenantId}/classrooms`, body);
  },
  update(tenantId: string, id: string, body: SaveRoom): Promise<Classroom> {
    return apiPut(`/api/v1/tenants/${tenantId}/classrooms/${id}`, body);
  },
  remove(tenantId: string, id: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/classrooms/${id}`);
  },
};
