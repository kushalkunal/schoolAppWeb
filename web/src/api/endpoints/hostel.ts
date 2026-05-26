import { apiClient, apiGet, apiPost } from '@/api/client';

export interface Hostel {
  id: string;
  schoolId: string;
  name: string;
  gender: string | null;
  address: string | null;
  wardenStaffId: string | null;
  totalRooms: number;
  capacity: number;
  active: boolean;
}

export interface HostelRoom {
  id: string;
  schoolId: string;
  hostelId: string;
  roomNumber: string;
  floor: number | null;
  roomType: string;
  capacity: number;
  currentOccupancy: number;
  notes: string | null;
  active: boolean;
}

export interface HostelAllocation {
  id: string;
  schoolId: string;
  roomId: string;
  studentId: string;
  allocatedFrom: string;
  allocatedUntil: string | null;
  status: 'ACTIVE' | 'VACATED';
  vacatedAt: string | null;
  vacatedReason: string | null;
}

export interface HostelVisitorLog {
  id: string;
  schoolId: string;
  hostelId: string;
  visitingStudentId: string | null;
  visitorName: string;
  visitorPhone: string | null;
  relation: string | null;
  idProof: string | null;
  purpose: string | null;
  inTime: string;
  outTime: string | null;
}

export const hostelApi = {
  // Hostels
  list(tenantId: string): Promise<Hostel[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hostel`);
  },
  create(tenantId: string, body: Partial<Hostel>): Promise<Hostel> {
    return apiPost(`/api/v1/tenants/${tenantId}/hostel`, body);
  },

  // Rooms
  rooms(tenantId: string, hostelId: string): Promise<HostelRoom[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hostel/${hostelId}/rooms`);
  },
  createRoom(tenantId: string, hostelId: string, body: Partial<HostelRoom>): Promise<HostelRoom> {
    return apiPost(`/api/v1/tenants/${tenantId}/hostel/${hostelId}/rooms`, body);
  },

  // Allocations
  allocate(tenantId: string, roomId: string, studentId: string, from?: string)
  : Promise<HostelAllocation> {
    const q = new URLSearchParams({ studentId });
    if (from) q.set('from', from);
    return apiClient.post(`/api/v1/tenants/${tenantId}/hostel/rooms/${roomId}/allocate?${q}`)
      .then((r) => r.data.data);
  },
  vacate(tenantId: string, allocationId: string, reason?: string): Promise<HostelAllocation> {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return apiPost(`/api/v1/tenants/${tenantId}/hostel/allocations/${allocationId}/vacate${q}`);
  },
  activeAllocations(tenantId: string): Promise<HostelAllocation[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hostel/allocations/active`);
  },

  // Visitors
  signIn(tenantId: string, body: Partial<HostelVisitorLog>): Promise<HostelVisitorLog> {
    return apiPost(`/api/v1/tenants/${tenantId}/hostel/visitors/sign-in`, body);
  },
  signOut(tenantId: string, logId: string): Promise<HostelVisitorLog> {
    return apiPost(`/api/v1/tenants/${tenantId}/hostel/visitors/${logId}/sign-out`);
  },
  inside(tenantId: string): Promise<HostelVisitorLog[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/hostel/visitors/inside`);
  },
};
