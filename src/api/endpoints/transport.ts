import { apiDelete, apiGet, apiPost } from '@/api/client';

export interface TransportRoute {
  id: string;
  schoolId: string;
  name: string;
  stops: Array<Record<string, unknown>>;
  farePaise: number;
  active: boolean;
}

export interface TransportVehicle {
  id: string;
  schoolId: string;
  registrationNo: string;
  driverStaffId: string | null;
  capacity: number;
  routeId: string | null;
  active: boolean;
}

export interface StudentTransportAssignment {
  id: string;
  schoolId: string;
  studentId: string;
  routeId: string;
  stopName: string | null;
  startDate: string;
  endDate: string | null;
}

export interface CreateRouteRequest {
  name: string;
  stops?: Array<Record<string, unknown>>;
  farePaise: number;
}

export interface CreateVehicleRequest {
  registrationNo: string;
  driverStaffId?: string;
  capacity: number;
  routeId?: string;
}

export interface AssignRequest {
  studentId: string;
  routeId: string;
  stopName?: string;
  startDate: string;
}

export const transportApi = {
  // Routes
  listRoutes(tenantId: string): Promise<TransportRoute[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/transport/routes`);
  },
  createRoute(tenantId: string, req: CreateRouteRequest): Promise<TransportRoute> {
    return apiPost(`/api/v1/tenants/${tenantId}/transport/routes`, req);
  },
  deactivateRoute(tenantId: string, routeId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/transport/routes/${routeId}`);
  },

  // Vehicles
  listVehicles(tenantId: string): Promise<TransportVehicle[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/transport/vehicles`);
  },
  createVehicle(tenantId: string, req: CreateVehicleRequest): Promise<TransportVehicle> {
    return apiPost(`/api/v1/tenants/${tenantId}/transport/vehicles`, req);
  },

  // Assignments
  assignmentsForStudent(tenantId: string, studentId: string): Promise<StudentTransportAssignment[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/transport/assignments/students/${studentId}`);
  },
  assign(tenantId: string, req: AssignRequest): Promise<StudentTransportAssignment> {
    return apiPost(`/api/v1/tenants/${tenantId}/transport/assignments`, req);
  },
  endAssignment(tenantId: string, assignmentId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/transport/assignments/${assignmentId}`);
  },
};
