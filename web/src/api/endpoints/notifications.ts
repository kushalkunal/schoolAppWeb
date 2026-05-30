import { apiClient, apiGet, apiPost } from '@/api/client';

export type NotificationStatus = 'QUEUED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';

export interface NotificationLogEntry {
  id: string;
  eventType: string;
  channel: string;
  recipientPhone: string;
  recipientName: string | null;
  studentId: string | null;
  messageBody: string | null;
  status: NotificationStatus;
  errorMessage: string | null;
  createdAt: string;
  sentAt: string | null;
  deliveredAt: string | null;
  readAt: string | null;
}

export interface NotificationLogsPage {
  items: NotificationLogEntry[];
  total: number;
  page: number;
  size: number;
}

export const notificationsApi = {
  list(
    tenantId: string,
    params?: { page?: number; size?: number; status?: string; eventType?: string },
  ): Promise<NotificationLogsPage> {
    return apiGet(`/api/v1/tenants/${tenantId}/notification-logs`, params as Record<string, unknown>);
  },
  async resend(tenantId: string, logId: string): Promise<void> {
    await apiClient.post(`/api/v1/tenants/${tenantId}/notification-logs/${logId}/resend`);
  },
};
