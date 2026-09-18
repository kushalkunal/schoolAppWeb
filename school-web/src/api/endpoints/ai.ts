import { apiPost } from '@/api/client';

/**
 * Wrapper for the school chatbot endpoint (Slice 19a/b). Each request is one shot —
 * no server-side memory yet. Multi-turn memory is a Slice 19+ TODO.
 */
export const aiApi = {
  chat(tenantId: string, query: string): Promise<{ reply: string }> {
    return apiPost(`/api/v1/tenants/${tenantId}/ai/chat`, { query });
  },
};
