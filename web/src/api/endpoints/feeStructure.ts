import { apiClient, apiGet, apiPost, apiPut } from '@/api/client';
import type { ImportResult } from '@/api/endpoints/imports';

// ---------- Types ----------

export type FeeStructureStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface FeeStructureVersionResponse {
  id: string;
  academicYearId: string;
  name: string;
  status: FeeStructureStatus;
  notes: string | null;
  activatedAt: string | null;
  archivedAt: string | null;
  createdAt: string;
}

export interface TermDto {
  termNumber: number;
  name: string;
  startDate: string; // ISO yyyy-MM-dd
  endDate: string;
  dueDate: string;
}

export interface MatrixRowDto {
  classId: string;
  feeHeadId: string;
  /** null = annual */
  termNumber: number | null;
  amountPaise: number;
  optional: boolean;
}

export interface MatrixResponse {
  version: FeeStructureVersionResponse;
  terms: TermDto[];
  rows: MatrixRowDto[];
}

export interface CreateVersionRequest {
  academicYearId: string;
  name: string;
  notes?: string;
}

export interface UpdateMatrixRequest {
  terms: TermDto[];
  rows: MatrixRowDto[];
}

export interface GenerateInvoicesRequest {
  termNumber?: number | null;
}

export interface GenerateInvoicesResponse {
  studentsProcessed: number;
  invoicesCreated: number;
  invoicesSkippedDuplicate: number;
  totalAmountPaise: number;
}

export interface MatrixImportRowResult {
  rowNumber: number;
  className: string;
  feeHeadName: string;
  termNumber: number | null;
  amountPaise: number;
  optional: boolean;
  error: string | null;
  ok: boolean;
}

export interface FeeHeadResponse {
  id: string;
  name: string;
  active: boolean;
}

export interface CurrentAcademicYearResponse {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  current: boolean;
}

// ---------- API ----------

export const feeStructureApi = {
  listVersions(tenantId: string): Promise<FeeStructureVersionResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/fee-structure/versions`);
  },

  createVersion(tenantId: string, req: CreateVersionRequest): Promise<FeeStructureVersionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/fee-structure/versions`, req);
  },

  getMatrix(tenantId: string, versionId: string): Promise<MatrixResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/fee-structure/versions/${versionId}`);
  },

  updateMatrix(
    tenantId: string,
    versionId: string,
    req: UpdateMatrixRequest,
  ): Promise<MatrixResponse> {
    return apiPut(`/api/v1/tenants/${tenantId}/fee-structure/versions/${versionId}/matrix`, req);
  },

  activate(tenantId: string, versionId: string): Promise<FeeStructureVersionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/fee-structure/versions/${versionId}/activate`);
  },

  archive(tenantId: string, versionId: string): Promise<FeeStructureVersionResponse> {
    return apiPost(`/api/v1/tenants/${tenantId}/fee-structure/versions/${versionId}/archive`);
  },

  generate(
    tenantId: string,
    versionId: string,
    req: GenerateInvoicesRequest,
  ): Promise<GenerateInvoicesResponse> {
    return apiPost(
      `/api/v1/tenants/${tenantId}/fee-structure/versions/${versionId}/generate`,
      req,
    );
  },

  importMatrix(
    tenantId: string,
    versionId: string,
    file: File,
    dryRun: boolean,
  ): Promise<ImportResult<MatrixImportRowResult>> {
    const form = new FormData();
    form.append('file', file);
    return apiClient
      .post(
        `/api/v1/tenants/${tenantId}/fee-structure/versions/${versionId}/import-matrix?dryRun=${dryRun}`,
        form,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      )
      .then((r) => r.data.data as ImportResult<MatrixImportRowResult>);
  },

  // ---------- Related lookups (lives here because the matrix editor needs them) ----------

  listFeeHeads(tenantId: string): Promise<FeeHeadResponse[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/fee-heads`);
  },

  currentAcademicYear(tenantId: string): Promise<CurrentAcademicYearResponse> {
    return apiGet(`/api/v1/tenants/${tenantId}/academic-years/current`);
  },
};
