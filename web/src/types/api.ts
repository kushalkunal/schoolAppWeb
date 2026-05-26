/**
 * Canonical API envelope shape — matches in.schoolapp.common.ApiResponse on the wire.
 * The shared axios client unwraps the envelope automatically; these types are for
 * features that need to access `meta` directly (pagination).
 */
export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: { code: string; message: string; details?: Record<string, unknown> };
  meta?: Meta;
}

export interface Meta {
  total?: number;
  page?: number;
  limit?: number;
  nextCursor?: string;
}

export interface PageResult<T> {
  items: T[];
  meta: Meta;
}
