import { apiClient, apiGet, apiPost } from '@/api/client';

export interface MenuItem {
  id: string;
  schoolId: string;
  name: string;
  category: string | null;
  description: string | null;
  pricePaise: number;
  imageUrl: string | null;
  available: boolean;
}

export interface Wallet {
  id: string;
  schoolId: string;
  studentId: string;
  balancePaise: number;
  totalToppedUpPaise: number;
  totalSpentPaise: number;
  lastToppedUpAt: string | null;
}

export interface WalletTransaction {
  id: string;
  schoolId: string;
  walletId: string;
  txnType: 'TOPUP' | 'DEBIT' | 'REFUND';
  amountPaise: number;
  refOrderId: string | null;
  balanceAfterPaise: number;
  notes: string | null;
  createdAt: string;
}

export interface CafeteriaOrder {
  id: string;
  schoolId: string;
  studentId: string;
  walletId: string | null;
  totalPaise: number;
  status: 'PLACED' | 'FULFILLED' | 'CANCELLED' | 'REFUNDED';
  placedAt: string;
  fulfilledAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  notes: string | null;
}

export interface OrderItem {
  id: string;
  orderId: string;
  menuItemId: string;
  quantity: number;
  unitPricePaise: number;
  lineTotalPaise: number;
}

export const cafeteriaApi = {
  // Menu
  menu(tenantId: string, availableOnly = false): Promise<MenuItem[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/cafeteria/menu?availableOnly=${availableOnly}`);
  },
  createMenuItem(tenantId: string, body: Partial<MenuItem>): Promise<MenuItem> {
    return apiPost(`/api/v1/tenants/${tenantId}/cafeteria/menu`, body);
  },

  // Wallet
  wallet(tenantId: string, studentId: string): Promise<Wallet> {
    return apiGet(`/api/v1/tenants/${tenantId}/cafeteria/wallets/${studentId}`);
  },
  topUp(tenantId: string, studentId: string, amountPaise: number, notes?: string)
  : Promise<WalletTransaction> {
    const q = new URLSearchParams({ amountPaise: String(amountPaise) });
    if (notes) q.set('notes', notes);
    return apiClient.post(`/api/v1/tenants/${tenantId}/cafeteria/wallets/${studentId}/topup?${q}`)
      .then((r) => r.data.data);
  },
  transactions(tenantId: string, walletId: string): Promise<WalletTransaction[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/cafeteria/wallets/${walletId}/transactions`);
  },

  // Orders
  placeOrder(tenantId: string, studentId: string,
             items: Array<{ menuItemId: string; quantity: number }>, notes?: string)
  : Promise<CafeteriaOrder> {
    return apiPost(`/api/v1/tenants/${tenantId}/cafeteria/orders`, { studentId, items, notes });
  },
  fulfill(tenantId: string, orderId: string): Promise<CafeteriaOrder> {
    return apiPost(`/api/v1/tenants/${tenantId}/cafeteria/orders/${orderId}/fulfill`);
  },
  cancel(tenantId: string, orderId: string, reason?: string): Promise<CafeteriaOrder> {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return apiPost(`/api/v1/tenants/${tenantId}/cafeteria/orders/${orderId}/cancel${q}`);
  },
  listOrders(tenantId: string): Promise<CafeteriaOrder[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/cafeteria/orders`);
  },
  studentOrders(tenantId: string, studentId: string): Promise<CafeteriaOrder[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/cafeteria/orders/student/${studentId}`);
  },
  orderItems(tenantId: string, orderId: string): Promise<OrderItem[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/cafeteria/orders/${orderId}/items`);
  },
};
