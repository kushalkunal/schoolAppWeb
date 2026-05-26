'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UtensilsCrossed, Plus, Wallet, CheckCircle2, XCircle } from 'lucide-react';
import { cafeteriaApi, type MenuItem } from '@/api/endpoints/cafeteria';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Badge } from '@/components/ui/Badge';
import { Stat } from '@/components/ui/Stat';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

export default function CafeteriaPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();
  const [menuOpen, setMenuOpen] = useState(false);

  const menuQ = useQuery({
    queryKey: ['cafeteria-menu', tenantId],
    queryFn: () => cafeteriaApi.menu(tenantId, false),
    enabled: !!tenantId,
    retry: false,
  });
  const ordersQ = useQuery({
    queryKey: ['cafeteria-orders', tenantId],
    queryFn: () => cafeteriaApi.listOrders(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  const fulfill = useMutation({
    mutationFn: (id: string) => cafeteriaApi.fulfill(tenantId, id),
    onSuccess: () => {
      toast.success('Order fulfilled');
      qc.invalidateQueries({ queryKey: ['cafeteria-orders', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  const cancelMut = useMutation({
    mutationFn: (id: string) => cafeteriaApi.cancel(tenantId, id, 'Cancelled from admin'),
    onSuccess: () => {
      toast.info('Order cancelled, wallet refunded');
      qc.invalidateQueries({ queryKey: ['cafeteria-orders', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });

  if (menuQ.isError && hasCode(menuQ.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader title="Cafeteria" icon={<UtensilsCrossed size={18} />} />
        <EmptyState
          icon={<UtensilsCrossed size={28} />}
          title="Cafeteria isn't enabled for your plan"
          description="Menu management, pre-paid wallets, and order tracking."
        />
      </div>
    );
  }

  const orders = ordersQ.data ?? [];
  const placed = orders.filter((o) => o.status === 'PLACED').length;
  const fulfilled = orders.filter((o) => o.status === 'FULFILLED').length;
  const revenue = orders.filter((o) => o.status !== 'CANCELLED').reduce((a, o) => a + o.totalPaise, 0);

  return (
    <div className="space-y-5">
      <PageHeader
        title="Cafeteria"
        description="Menu, pre-paid wallets, orders"
        icon={<UtensilsCrossed size={18} />}
        actions={
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button onClick={() => setMenuOpen(true)}><Plus size={14} /> Add menu item</Button>
          </RequireRole>
        }
      />

      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Stat label="Pending orders" value={placed}
              icon={<UtensilsCrossed size={16} />} tone={placed > 0 ? 'warning' : 'success'} />
        <Stat label="Fulfilled" value={fulfilled} icon={<CheckCircle2 size={16} />} tone="success" />
        <Stat label="Revenue" value={`₹${(revenue / 100).toLocaleString('en-IN')}`}
              icon={<Wallet size={16} />} tone="accent" />
      </div>

      {/* Menu */}
      <Card padding="none" className="overflow-hidden">
        <CardHeader>
          <CardTitle>Menu</CardTitle>
          <CardDescription>What's on offer this term</CardDescription>
        </CardHeader>
        {menuQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
        {menuQ.data && menuQ.data.length === 0 && (
          <CardBody><p className="text-sm text-slate-500 py-6 text-center">No items on the menu yet.</p></CardBody>
        )}
        {menuQ.data && menuQ.data.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 p-3">
            {menuQ.data.map((m) => (
              <div key={m.id} className="rounded-brand border border-slate-200 p-3 flex items-center gap-3">
                <span className="w-9 h-9 rounded-brand bg-accent-soft text-accent grid place-items-center">
                  <UtensilsCrossed size={16} />
                </span>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-slate-900 truncate">{m.name}</div>
                  <div className="text-[11px] text-slate-500">
                    {m.category && `${m.category} · `}
                    ₹{(m.pricePaise / 100).toLocaleString('en-IN')}
                  </div>
                </div>
                {!m.available && <Badge tone="warning" size="sm">Hidden</Badge>}
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Recent orders */}
      <Card padding="none" className="overflow-hidden">
        <CardHeader>
          <CardTitle>Recent orders</CardTitle>
          <CardDescription>Fulfill or cancel to refund the student's wallet</CardDescription>
        </CardHeader>
        {ordersQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
        {orders.length === 0 && (
          <CardBody><p className="text-sm text-slate-500 py-6 text-center">No orders yet.</p></CardBody>
        )}
        {orders.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {orders.slice(0, 30).map((o) => (
              <li key={o.id} className="px-5 py-3 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold tabular-nums">
                      ₹{(o.totalPaise / 100).toLocaleString('en-IN')}
                    </span>
                    <StatusBadge status={o.status} />
                  </div>
                  <div className="text-[11px] text-slate-500">
                    Placed {new Date(o.placedAt).toLocaleString('en-IN')}
                  </div>
                </div>
                {o.status === 'PLACED' && (
                  <RequireRole roles={OWNER_OR_ADMIN}>
                    <div className="flex gap-1">
                      <Button size="sm" variant="accent" onClick={() => fulfill.mutate(o.id)} loading={fulfill.isPending}>
                        <CheckCircle2 size={14} /> Fulfill
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => cancelMut.mutate(o.id)} loading={cancelMut.isPending}>
                        <XCircle size={14} />
                      </Button>
                    </div>
                  </RequireRole>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <CreateMenuItemModal open={menuOpen} onClose={() => setMenuOpen(false)} tenantId={tenantId} />
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, 'neutral' | 'success' | 'warning' | 'danger' | 'info'> = {
    PLACED: 'warning',
    FULFILLED: 'success',
    CANCELLED: 'danger',
    REFUNDED: 'info',
  };
  return <Badge tone={map[status] ?? 'neutral'} size="sm">{status.toLowerCase()}</Badge>;
}

function CreateMenuItemModal({ open, onClose, tenantId }:
  { open: boolean; onClose: () => void; tenantId: string }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState<Partial<MenuItem>>({ name: '', category: '', pricePaise: 0, available: true });
  const [rupees, setRupees] = useState('');
  const create = useMutation({
    mutationFn: () => cafeteriaApi.createMenuItem(tenantId, {
      ...form,
      pricePaise: Math.round(Number(rupees || 0) * 100),
    }),
    onSuccess: () => {
      toast.success('Menu item added');
      qc.invalidateQueries({ queryKey: ['cafeteria-menu', tenantId] });
      onClose();
      setForm({ name: '', category: '', pricePaise: 0, available: true });
      setRupees('');
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={open} onClose={onClose} title="Add menu item">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Item name" required value={form.name ?? ''}
               onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Category</span>
          <select value={form.category ?? ''}
            onChange={(e) => setForm({ ...form, category: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">— Other —</option>
            {['BREAKFAST', 'LUNCH', 'SNACK', 'DINNER', 'BEVERAGE'].map((c) => (
              <option key={c} value={c}>{c.charAt(0) + c.slice(1).toLowerCase()}</option>
            ))}
          </select>
        </label>
        <Input label="Price (₹)" type="number" min="0" step="0.50" required value={rupees}
               onChange={(e) => setRupees(e.target.value)} />
        <Input label="Description (optional)" value={form.description ?? ''}
               onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.name || !rupees}>
            Add item
          </Button>
        </div>
      </form>
    </Modal>
  );
}
