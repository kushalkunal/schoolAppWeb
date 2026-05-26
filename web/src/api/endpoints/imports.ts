import { apiClient } from '@/api/client';

export interface ImportRowError {
  row: number;
  message: string;
}

export interface ImportResult<T> {
  dryRun: boolean;
  totalRowsRead: number;
  accepted: T[];
  errors: ImportRowError[];
  skippedDuplicates: number[];
  acceptedCount: number;
  errorCount: number;
  duplicateCount: number;
}

/**
 * Wrappers for the bulk-import endpoints (Slice 13 backend). Each call sends a
 * multipart/form-data request with the CSV as the `file` part. {@code dryRun}
 * defaults to true so the first upload is always a safe preview.
 */
export const importsApi = {
  students<T = unknown>(tenantId: string, file: File, dryRun = true): Promise<ImportResult<T>> {
    const form = new FormData();
    form.append('file', file);
    return apiClient
      .post(`/api/v1/tenants/${tenantId}/imports/students?dryRun=${dryRun}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.data as ImportResult<T>);
  },

  staff<T = unknown>(tenantId: string, file: File, dryRun = true): Promise<ImportResult<T>> {
    const form = new FormData();
    form.append('file', file);
    return apiClient
      .post(`/api/v1/tenants/${tenantId}/imports/staff?dryRun=${dryRun}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.data as ImportResult<T>);
  },
};
