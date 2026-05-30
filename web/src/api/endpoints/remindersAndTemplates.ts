import { apiClient, apiGet, apiPost, apiPut, apiDelete } from '@/api/client';
import type { ApiResponse } from '@/types/api';

export interface FeeReminderSchedule {
  id: string;
  name: string;
  triggerType: 'BEFORE_DUE' | 'ON_DUE' | 'AFTER_DUE';
  daysOffset: number;
  includeUpiLink: boolean;
  active: boolean;
}

export interface MessageTemplate {
  id: string | null;
  templateKey: string;
  bodyTemplate: string | null;   // null → school has no override yet
  defaultBody: string;
}

export interface UpsertTemplateRequest {
  templateKey: string;
  bodyTemplate: string;
}

export const feeRemindersApi = {
  list(tenantId: string): Promise<FeeReminderSchedule[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/fee-reminder-schedules`);
  },
  create(tenantId: string, body: Omit<FeeReminderSchedule, 'id'>): Promise<FeeReminderSchedule> {
    return apiPost(`/api/v1/tenants/${tenantId}/fee-reminder-schedules`, body);
  },
  update(tenantId: string, id: string, body: Omit<FeeReminderSchedule, 'id'>): Promise<FeeReminderSchedule> {
    return apiPut(`/api/v1/tenants/${tenantId}/fee-reminder-schedules/${id}`, body);
  },
  async remove(tenantId: string, id: string): Promise<void> {
    await apiClient.delete(`/api/v1/tenants/${tenantId}/fee-reminder-schedules/${id}`);
  },
};

export const messageTemplatesApi = {
  list(tenantId: string): Promise<MessageTemplate[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/message-templates`);
  },
  upsert(tenantId: string, key: string, body: UpsertTemplateRequest): Promise<MessageTemplate> {
    return apiPut(`/api/v1/tenants/${tenantId}/message-templates/${key}`, body);
  },
  async reset(tenantId: string, key: string): Promise<void> {
    await apiClient.delete(`/api/v1/tenants/${tenantId}/message-templates/${key}`);
  },
};
