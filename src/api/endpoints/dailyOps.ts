import { apiClient, apiDelete, apiGet, apiPost, apiPut } from '@/api/client';

// ============================ Visitor ============================

export interface VisitorResponse {
  id: string; name: string; phone: string | null; purpose: string | null;
  hostStaffId: string | null; hostStudentId: string | null;
  badgeNumber: string | null; photoUrl: string | null;
  inAt: string; outAt: string | null; notes: string | null;
}

export interface CreateVisitorRequest {
  name: string; phone?: string; purpose?: string;
  hostStaffId?: string; hostStudentId?: string;
  badgeNumber?: string; photoUrl?: string; notes?: string;
}

export const visitorApi = {
  checkIn(tenantId: string, req: CreateVisitorRequest): Promise<VisitorResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/visitors`, req);
  },
  checkOut(tenantId: string, id: string): Promise<VisitorResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/visitors/${id}/check-out`);
  },
  open(tenantId: string): Promise<VisitorResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/visitors/open`);
  },
  async list(tenantId: string, page = 0, size = 50): Promise<{ content: VisitorResponse[]; totalElements: number }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/visitors`, { params: { page, size } });
    return res.data.data;
  },
};

// ============================ Cash recon ============================

export interface DayTotalsResponse {
  date: string;
  expectedCashPaise: number; expectedUpiPaise: number;
  expectedChequePaise: number; expectedOtherPaise: number;
  totalPaise: number; paymentCount: number;
}

export interface CashReconciliationResponse {
  id: string; closedOnDate: string; closedById: string;
  expectedCashPaise: number; expectedUpiPaise: number; expectedChequePaise: number; expectedOtherPaise: number;
  countedCashPaise:  number; countedUpiPaise:  number; countedChequePaise:  number; countedOtherPaise:  number;
  variancePaise: number; notes: string | null; closedAt: string;
}

export interface CloseDrawerRequest {
  date: string;
  countedCashPaise: number; countedUpiPaise: number;
  countedChequePaise: number; countedOtherPaise: number;
  notes?: string;
}

export const cashReconApi = {
  expected(tenantId: string, date: string): Promise<DayTotalsResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/cash-recon/expected?date=${date}`);
  },
  close(tenantId: string, req: CloseDrawerRequest): Promise<CashReconciliationResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/cash-recon/close`, req);
  },
  history(tenantId: string): Promise<CashReconciliationResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/cash-recon/history`);
  },
};

// ============================ Expenses ============================

export interface ExpenseResponse {
  id: string; categoryId: string | null; amountPaise: number; spentOn: string;
  vendor: string | null; description: string | null; receiptUrl: string | null;
  paymentMode: string | null;
}
export interface CategoryDto { id: string; name: string; active: boolean; }
export interface CreateExpenseRequest {
  categoryId?: string; amountPaise: number; spentOn: string;
  vendor?: string; description?: string; receiptUrl?: string; paymentMode?: string;
}

export const expenseApi = {
  categories(tenantId: string): Promise<CategoryDto[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/expenses/categories`);
  },
  createCategory(tenantId: string, name: string): Promise<CategoryDto> {
    return apiPost(`/api/v1/tenants/${tenantId}/expenses/categories`, { name });
  },
  create(tenantId: string, req: CreateExpenseRequest): Promise<ExpenseResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/expenses`, req);
  },
  async list(tenantId: string, page = 0, size = 50): Promise<{ content: ExpenseResponse[]; totalElements: number }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/expenses`, { params: { page, size } });
    return res.data.data;
  },
  delete(tenantId: string, id: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/expenses/${id}`);
  },
};

// ============================ Incidents ============================

export interface IncidentResponse {
  id: string; studentId: string; severity: string; incidentType: string; points: number;
  occurredOn: string; description: string; actionTaken: string | null; parentNotified: boolean;
}
export interface CreateIncidentRequest {
  studentId: string; severity: 'MINOR' | 'MAJOR' | 'SEVERE';
  incidentType: 'MERIT' | 'DEMERIT' | 'DISCIPLINE' | 'ACADEMIC';
  points: number; occurredOn: string;
  description: string; actionTaken?: string; notifyParent: boolean;
}

export const incidentApi = {
  create(tenantId: string, req: CreateIncidentRequest): Promise<IncidentResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/incidents`, req);
  },
  async list(tenantId: string, page = 0, size = 50): Promise<{ content: IncidentResponse[]; totalElements: number }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/incidents`, { params: { page, size } });
    return res.data.data;
  },
  byStudent(tenantId: string, studentId: string): Promise<IncidentResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/incidents/by-student/${studentId}`);
  },
  delete(tenantId: string, id: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/incidents/${id}`);
  },
};

// ============================ Infirmary ============================

export interface VisitResponse {
  id: string; studentId: string; visitedAt: string;
  complaint: string; treatment: string | null; medicineGiven: string | null;
  temperatureC: number | null; pulse: number | null;
  sentHome: boolean; parentNotified: boolean; notes: string | null;
}
export interface CreateVisitRequest {
  studentId: string; complaint: string;
  treatment?: string; medicineGiven?: string;
  temperatureC?: number; pulse?: number;
  sentHome: boolean; notes?: string;
}
export interface MedicalRecordDto {
  studentId: string; bloodGroup: string | null; allergies: string | null;
  chronicConditions: string | null; medications: string | null;
  emergencyContact: string | null; emergencyPhone: string | null;
}

export const infirmaryApi = {
  record(tenantId: string, req: CreateVisitRequest): Promise<VisitResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/infirmary/visits`, req);
  },
  async list(tenantId: string, page = 0, size = 50): Promise<{ content: VisitResponse[]; totalElements: number }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/infirmary/visits`, { params: { page, size } });
    return res.data.data;
  },
  byStudent(tenantId: string, studentId: string): Promise<VisitResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/infirmary/visits/by-student/${studentId}`);
  },
  getMedical(tenantId: string, studentId: string): Promise<MedicalRecordDto> {
    return apiGet(`/api/v1/tenants/${tenantId}/infirmary/medical/${studentId}`);
  },
  upsertMedical(tenantId: string, studentId: string, req: MedicalRecordDto): Promise<MedicalRecordDto> {
    return apiPut(`/api/v1/tenants/${tenantId}/infirmary/medical/${studentId}`, req);
  },
};

// ============================ Vault ============================

export interface VaultDocument {
  id: string; studentId: string; docType: string;
  fileName: string; fileUrl: string; mimeType: string | null;
  sizeBytes: number | null; uploadedAt: string; notes: string | null;
}

export const vaultApi = {
  list(tenantId: string, studentId: string): Promise<VaultDocument[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/vault/students/${studentId}`);
  },
  upload(tenantId: string, studentId: string, file: File, docType: string, notes?: string)
    : Promise<{ id: string; docType: string; fileName: string; fileUrl: string }> {
    const form = new FormData();
    form.append('file', file);
    form.append('docType', docType);
    if (notes) form.append('notes', notes);
    return apiClient
      .post(`/api/v1/tenants/${tenantId}/vault/students/${studentId}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.data);
  },
  delete(tenantId: string, id: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/vault/${id}`);
  },
};

// ============================ PTM ============================

export interface PtmSlot {
  id: string; teacherId: string; sectionId: string | null; slotDate: string;
  startTime: string; endTime: string; capacity: number; bookedCount: number;
}
export interface PtmBooking {
  id: string; slotId: string; studentId: string;
  parentId: string | null; status: string; notes: string | null; createdAt: string;
}

export const ptmApi = {
  createSlot(tenantId: string, body: { teacherId: string; sectionId?: string; date: string; startTime: string; endTime: string; capacity: number }) {
    return apiPost<PtmSlot>(`/api/v1/tenants/${tenantId}/ptm/slots`, body);
  },
  slots(tenantId: string, date: string): Promise<PtmSlot[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/ptm/slots?date=${date}`);
  },
  book(tenantId: string, slotId: string, studentId: string, notes?: string): Promise<PtmBooking> {
    return apiPost(`/api/v1/tenants/${tenantId}/ptm/slots/${slotId}/book`, { studentId, notes });
  },
  cancel(tenantId: string, bookingId: string): Promise<void> {
    return apiPost(`/api/v1/tenants/${tenantId}/ptm/bookings/${bookingId}/cancel`);
  },
  byStudent(tenantId: string, studentId: string): Promise<PtmBooking[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/ptm/bookings/by-student/${studentId}`);
  },
};

// ============================ Unified Inbox ============================

export interface ParentMessage {
  id: string; studentId: string | null; parentId: string | null;
  channel: 'WHATSAPP' | 'EMAIL' | 'SMS';
  category: string;
  subject: string | null; body: string; mediaUrl: string | null;
  status: string; sentAt: string;
  deliveredAt: string | null; readAt: string | null;
}

export const inboxApi = {
  async list(tenantId: string, page = 0, size = 100): Promise<{ content: ParentMessage[]; totalElements: number }> {
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/parent-messages`, { params: { page, size } });
    return res.data.data;
  },
  byStudent(tenantId: string, studentId: string): Promise<ParentMessage[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/parent-messages/by-student/${studentId}`);
  },
};
