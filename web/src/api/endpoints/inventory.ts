import { apiClient, apiGet, apiPost } from '@/api/client';

export type InventoryStatus = 'AVAILABLE' | 'ISSUED' | 'UNDER_MAINTENANCE' | 'LOST' | 'RETIRED';
export type ReturnCondition = 'GOOD' | 'DAMAGED' | 'LOST';

export interface InventoryCategory {
  id: string;
  schoolId: string;
  name: string;
  description: string | null;
}

export interface InventoryItem {
  id: string;
  schoolId: string;
  categoryId: string | null;
  assetTag: string | null;
  name: string;
  description: string | null;
  serialNumber: string | null;
  quantity: number;
  unitCostPaise: number | null;
  purchaseDate: string | null;
  purchaseInvoiceUrl: string | null;
  status: InventoryStatus;
  location: string | null;
  notes: string | null;
}

export interface InventoryIssuance {
  id: string;
  schoolId: string;
  itemId: string;
  issuedToStaffId: string | null;
  issuedToStudentId: string | null;
  issuedAt: string;
  expectedReturnAt: string | null;
  returnedAt: string | null;
  returnCondition: ReturnCondition | null;
  notes: string | null;
}

export interface InventoryMaintenance {
  id: string;
  schoolId: string;
  itemId: string;
  performedAt: string;
  costPaise: number | null;
  description: string;
  performedBy: string | null;
}

export const inventoryApi = {
  // Categories
  listCategories(tenantId: string): Promise<InventoryCategory[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/inventory/categories`);
  },
  createCategory(tenantId: string, body: Partial<InventoryCategory>): Promise<InventoryCategory> {
    return apiPost(`/api/v1/tenants/${tenantId}/inventory/categories`, body);
  },

  // Items
  listItems(tenantId: string, status?: InventoryStatus, categoryId?: string)
  : Promise<InventoryItem[]> {
    const q = new URLSearchParams();
    if (status) q.set('status', status);
    if (categoryId) q.set('categoryId', categoryId);
    const qs = q.toString() ? `?${q}` : '';
    return apiGet(`/api/v1/tenants/${tenantId}/inventory/items${qs}`);
  },
  createItem(tenantId: string, body: Partial<InventoryItem>): Promise<InventoryItem> {
    return apiPost(`/api/v1/tenants/${tenantId}/inventory/items`, body);
  },
  getItem(tenantId: string, itemId: string): Promise<InventoryItem> {
    return apiGet(`/api/v1/tenants/${tenantId}/inventory/items/${itemId}`);
  },

  // Issuances
  issue(tenantId: string, itemId: string, opts: {
    staffId?: string; studentId?: string; expectedReturn?: string; notes?: string;
  }): Promise<InventoryIssuance> {
    const q = new URLSearchParams();
    if (opts.staffId)        q.set('staffId',        opts.staffId);
    if (opts.studentId)      q.set('studentId',      opts.studentId);
    if (opts.expectedReturn) q.set('expectedReturn', opts.expectedReturn);
    if (opts.notes)          q.set('notes',          opts.notes);
    return apiClient.post(`/api/v1/tenants/${tenantId}/inventory/items/${itemId}/issue?${q}`)
      .then((r) => r.data.data);
  },
  returnItem(tenantId: string, issuanceId: string, condition?: ReturnCondition, notes?: string)
  : Promise<InventoryIssuance> {
    const q = new URLSearchParams();
    if (condition) q.set('condition', condition);
    if (notes)     q.set('notes',     notes);
    const qs = q.toString() ? `?${q}` : '';
    return apiPost(`/api/v1/tenants/${tenantId}/inventory/issuances/${issuanceId}/return${qs}`);
  },
  outstanding(tenantId: string): Promise<InventoryIssuance[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/inventory/issuances/outstanding`);
  },
};
