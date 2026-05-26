import { apiClient, apiGet, apiPost } from '@/api/client';
import type { AdmissionResponse, AdmissionStatus, EnquiryRequest } from '@/types/domain';

export interface AdmissionsPage {
  items: AdmissionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const admissionsApi = {
  /** Auth-side enquiry create — same shape as the public enquiry endpoint. */
  create(tenantId: string, req: EnquiryRequest): Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions`, req);
  },

  async list(tenantId: string, params?: { status?: AdmissionStatus; page?: number; size?: number })
  : Promise<AdmissionsPage> {
    const q = new URLSearchParams();
    if (params?.status) q.set('status', params.status);
    if (params?.page != null) q.set('page', String(params.page));
    if (params?.size != null) q.set('size', String(params.size));
    const res = await apiClient.get(`/api/v1/tenants/${tenantId}/admissions${q.toString() ? `?${q}` : ''}`);
    return res.data.data as AdmissionsPage;
  },

  get(tenantId: string, id: string): Promise<AdmissionResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/admissions/${id}`);
  },

  // Lifecycle transitions
  submitApplication(tenantId: string, id: string, patch: Record<string, unknown> = {})
  : Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/application`, patch);
  },
  scheduleTest(tenantId: string, id: string, body: { scheduledAt: string; venue?: string })
  : Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/test/schedule`, body);
  },
  recordTestResult(tenantId: string, id: string, body: Record<string, unknown>)
  : Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/test/result`, body);
  },
  makeOffer(tenantId: string, id: string, offerLetterUrl?: string): Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/offer`, { offerLetterUrl });
  },
  accept(tenantId: string, id: string): Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/offer/accept`);
  },
  decline(tenantId: string, id: string, reason?: string): Promise<AdmissionResponse> {
    const qs = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/offer/decline${qs}`);
  },
  reject(tenantId: string, id: string, reason?: string): Promise<AdmissionResponse> {
    const qs = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/reject${qs}`);
  },
  enroll(tenantId: string, id: string, sectionId: string): Promise<AdmissionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/admissions/${id}/enroll?sectionId=${sectionId}`);
  },
};
