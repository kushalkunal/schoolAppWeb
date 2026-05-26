'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Package, Plus, ArrowRightLeft, RotateCcw, Search, AlertTriangle,
} from 'lucide-react';
import {
  inventoryApi, type InventoryCategory, type InventoryItem,
  type InventoryStatus, type ReturnCondition,
} from '@/api/endpoints/inventory';
import { studentsApi } from '@/api/endpoints/students';
import { schoolApi } from '@/api/endpoints/school';
import { StudentPicker } from '@/components/pickers/StudentPicker';
import { StaffPicker } from '@/components/pickers/StaffPicker';
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

const STATUS_TONE: Record<InventoryStatus, 'success' | 'warning' | 'danger' | 'info' | 'neutral'> = {
  AVAILABLE:         'success',
  ISSUED:            'info',
  UNDER_MAINTENANCE: 'warning',
  LOST:              'danger',
  RETIRED:           'neutral',
};

export default function InventoryPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  const [search, setSearch]    = useState('');
  const [statusFilter, setStatusFilter] = useState<InventoryStatus | ''>('');
  const [itemOpen, setItemOpen]     = useState(false);
  const [issueOpen, setIssueOpen]   = useState<InventoryItem | null>(null);
  const [catOpen, setCatOpen]       = useState(false);

  const itemsQ = useQuery({
    queryKey: ['inventory-items', tenantId, statusFilter],
    queryFn: () => inventoryApi.listItems(tenantId, statusFilter || undefined),
    enabled: !!tenantId,
    retry: false,
  });
  const catsQ = useQuery({
    queryKey: ['inventory-cats', tenantId],
    queryFn: () => inventoryApi.listCategories(tenantId),
    enabled: !!tenantId,
  });

  const filtered = useMemo(() => {
    const list = itemsQ.data ?? [];
    if (!search.trim()) return list;
    const s = search.toLowerCase();
    return list.filter((i) =>
      i.name.toLowerCase().includes(s) ||
      (i.assetTag ?? '').toLowerCase().includes(s) ||
      (i.serialNumber ?? '').toLowerCase().includes(s),
    );
  }, [itemsQ.data, search]);

  if (itemsQ.isError && hasCode(itemsQ.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader title="Inventory" icon={<Package size={18} />} />
        <EmptyState
          icon={<Package size={28} />}
          title="Inventory isn't enabled for your plan"
          description="Track assets, issue to staff/students, log maintenance."
        />
      </div>
    );
  }

  const items = itemsQ.data ?? [];
  const counts = {
    total: items.length,
    available: items.filter((i) => i.status === 'AVAILABLE').length,
    issued: items.filter((i) => i.status === 'ISSUED').length,
    maintenance: items.filter((i) => i.status === 'UNDER_MAINTENANCE').length,
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Inventory"
        description="Assets, issuances, and maintenance log"
        icon={<Package size={18} />}
        actions={
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button size="sm" variant="secondary" onClick={() => setCatOpen(true)}>
              <Plus size={14} /> Category
            </Button>
            <Button size="sm" onClick={() => setItemOpen(true)}>
              <Plus size={14} /> Add asset
            </Button>
          </RequireRole>
        }
      />

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Stat label="Total assets" value={counts.total} icon={<Package size={16} />} tone="primary" />
        <Stat label="Available" value={counts.available} icon={<Package size={16} />} tone="success" />
        <Stat label="Issued" value={counts.issued} icon={<ArrowRightLeft size={16} />} tone="info" />
        <Stat label="In maintenance" value={counts.maintenance} icon={<AlertTriangle size={16} />}
              tone={counts.maintenance > 0 ? 'warning' : 'success'} />
      </div>

      {/* Filters */}
      <Card padding="md" className="flex flex-wrap items-end gap-3">
        <label className="relative flex-1 max-w-sm">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name, asset tag, or serial…"
            className="pl-9 pr-3 py-2 rounded-brand border border-slate-300 text-sm w-full focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          />
        </label>
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Status</span>
          <select value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as InventoryStatus | '')}
            className="mt-1.5 block rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">All</option>
            {(['AVAILABLE', 'ISSUED', 'UNDER_MAINTENANCE', 'LOST', 'RETIRED'] as InventoryStatus[]).map((s) => (
              <option key={s} value={s}>{s.replace(/_/g, ' ').toLowerCase()}</option>
            ))}
          </select>
        </label>
      </Card>

      {/* Items table */}
      {itemsQ.isLoading && <Skeleton className="h-48" />}
      {filtered.length === 0 && !itemsQ.isLoading && (
        <EmptyState
          icon={<Package size={28} />}
          title={search ? 'No assets match your search' : 'No assets yet'}
          description={!search ? 'Add your first asset to track issuances and maintenance.' : 'Try a different filter.'}
        />
      )}
      {filtered.length > 0 && (
        <Card padding="none" className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-[11px] uppercase tracking-wide text-slate-500 border-b border-slate-200">
                  <th className="text-left py-2.5 px-5">Asset</th>
                  <th className="text-left">Tag / SN</th>
                  <th className="text-left">Category</th>
                  <th className="text-left">Status</th>
                  <th className="text-left">Location</th>
                  <th className="px-5"></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((i) => (
                  <tr key={i.id} className="border-b border-slate-100 hover:bg-slate-50/60">
                    <td className="py-3 px-5">
                      <div className="font-medium text-slate-900">{i.name}</div>
                      {i.description && <div className="text-[11px] text-slate-500">{i.description}</div>}
                    </td>
                    <td className="text-slate-600 font-mono text-xs">
                      {i.assetTag || i.serialNumber || '—'}
                    </td>
                    <td className="text-slate-600">
                      {(catsQ.data ?? []).find((c) => c.id === i.categoryId)?.name ?? '—'}
                    </td>
                    <td>
                      <Badge tone={STATUS_TONE[i.status]} size="sm" dot>
                        {i.status.replace(/_/g, ' ').toLowerCase()}
                      </Badge>
                    </td>
                    <td className="text-slate-500 text-xs">{i.location ?? '—'}</td>
                    <td className="px-5">
                      <RequireRole roles={OWNER_OR_ADMIN}>
                        {i.status === 'AVAILABLE' && (
                          <Button size="sm" variant="subtle" onClick={() => setIssueOpen(i)}>
                            <ArrowRightLeft size={12} /> Issue
                          </Button>
                        )}
                      </RequireRole>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <CreateItemModal open={itemOpen} onClose={() => setItemOpen(false)}
                       tenantId={tenantId} categories={catsQ.data ?? []} />
      <CreateCategoryModal open={catOpen} onClose={() => setCatOpen(false)} tenantId={tenantId} />
      {issueOpen && (
        <IssueItemModal item={issueOpen} tenantId={tenantId}
                        onClose={() => setIssueOpen(null)} />
      )}

      <OutstandingPanel tenantId={tenantId} />
    </div>
  );
}

// Outstanding issuances panel
function OutstandingPanel({ tenantId }: { tenantId: string }) {
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({
    queryKey: ['inv-outstanding', tenantId],
    queryFn: () => inventoryApi.outstanding(tenantId),
    enabled: !!tenantId,
  });
  const [returnOpen, setReturnOpen] = useState<{ id: string; itemId: string } | null>(null);
  const list = q.data ?? [];
  if (list.length === 0) return null;

  return (
    <>
      <Card padding="none" className="overflow-hidden">
        <CardHeader>
          <CardTitle>Outstanding ({list.length})</CardTitle>
          <CardDescription>Issued but not yet returned</CardDescription>
        </CardHeader>
        <ul className="divide-y divide-slate-100">
          {list.map((iss) => (
            <li key={iss.id} className="px-5 py-3 flex items-center justify-between">
              <div className="min-w-0">
                <div className="text-sm font-medium text-slate-900">Issuance {iss.id.slice(0, 8)}</div>
                <div className="text-xs text-slate-500">
                  Issued {new Date(iss.issuedAt).toLocaleString('en-IN')}
                  {iss.expectedReturnAt && ` · Expected back ${new Date(iss.expectedReturnAt).toLocaleDateString('en-IN')}`}
                </div>
              </div>
              <RequireRole roles={OWNER_OR_ADMIN}>
                <Button size="sm" variant="secondary"
                        onClick={() => setReturnOpen({ id: iss.id, itemId: iss.itemId })}>
                  <RotateCcw size={14} /> Return
                </Button>
              </RequireRole>
            </li>
          ))}
        </ul>
      </Card>

      {returnOpen && (
        <ReturnModal
          issuanceId={returnOpen.id}
          tenantId={tenantId}
          onClose={() => setReturnOpen(null)}
        />
      )}
    </>
  );
}

function ReturnModal({ tenantId, issuanceId, onClose }:
  { tenantId: string; issuanceId: string; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [condition, setCondition] = useState<ReturnCondition>('GOOD');
  const [notes, setNotes] = useState('');
  const ret = useMutation({
    mutationFn: () => inventoryApi.returnItem(tenantId, issuanceId, condition, notes || undefined),
    onSuccess: () => {
      toast.success('Returned');
      qc.invalidateQueries({ queryKey: ['inv-outstanding', tenantId] });
      qc.invalidateQueries({ queryKey: ['inventory-items', tenantId] });
      onClose();
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={true} onClose={onClose} title="Return asset">
      <form onSubmit={(e) => { e.preventDefault(); ret.mutate(); }} className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Condition</span>
          <select value={condition}
            onChange={(e) => setCondition(e.target.value as ReturnCondition)}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="GOOD">Good — back to available</option>
            <option value="DAMAGED">Damaged — send to maintenance</option>
            <option value="LOST">Lost — mark as lost</option>
          </select>
        </label>
        <Input label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={ret.isPending}>Confirm return</Button>
        </div>
      </form>
    </Modal>
  );
}

function CreateItemModal({ open, onClose, tenantId, categories }:
  { open: boolean; onClose: () => void; tenantId: string; categories: InventoryCategory[] }) {
  const qc = useQueryClient();
  const toast = useToast();
  const initial = { name: '', assetTag: '', categoryId: '', location: '', quantity: 1 };
  const [form, setForm] = useState(initial);
  const create = useMutation({
    mutationFn: () => inventoryApi.createItem(tenantId, {
      ...form,
      categoryId: form.categoryId || null,
    } as Partial<InventoryItem>),
    onSuccess: () => {
      toast.success('Asset added');
      qc.invalidateQueries({ queryKey: ['inventory-items', tenantId] });
      onClose();
      setForm(initial);
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={open} onClose={onClose} title="Add asset">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Name" required value={form.name}
               onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <Input label="Asset tag" value={form.assetTag}
               onChange={(e) => setForm({ ...form, assetTag: e.target.value })} />
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Category</span>
          <select value={form.categoryId}
            onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">— None —</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <div className="grid grid-cols-2 gap-3">
          <Input label="Location" value={form.location}
                 onChange={(e) => setForm({ ...form, location: e.target.value })} />
          <Input label="Quantity" type="number" min={1} value={form.quantity}
                 onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })} />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.name}>Add asset</Button>
        </div>
      </form>
    </Modal>
  );
}

function CreateCategoryModal({ open, onClose, tenantId }:
  { open: boolean; onClose: () => void; tenantId: string }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [name, setName] = useState('');
  const create = useMutation({
    mutationFn: () => inventoryApi.createCategory(tenantId, { name }),
    onSuccess: () => {
      toast.success('Category added');
      qc.invalidateQueries({ queryKey: ['inventory-cats', tenantId] });
      onClose();
      setName('');
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={open} onClose={onClose} title="Add category">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Category name" required value={name}
               onChange={(e) => setName(e.target.value)}
               placeholder="e.g. Laptops, Lab equipment, Books" />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!name}>Add</Button>
        </div>
      </form>
    </Modal>
  );
}

function IssueItemModal({ item, tenantId, onClose }:
  { item: InventoryItem; tenantId: string; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [target, setTarget] = useState<'staff' | 'student'>('staff');
  const [staffId, setStaffId] = useState<string | null>(null);
  const [studentId, setStudentId] = useState<string | null>(null);
  const [expected, setExpected] = useState('');
  const [notes, setNotes] = useState('');

  const issue = useMutation({
    mutationFn: () => inventoryApi.issue(tenantId, item.id, {
      staffId: target === 'staff' ? staffId ?? undefined : undefined,
      studentId: target === 'student' ? studentId ?? undefined : undefined,
      expectedReturn: expected || undefined,
      notes: notes || undefined,
    }),
    onSuccess: () => {
      toast.success('Asset issued');
      qc.invalidateQueries({ queryKey: ['inventory-items', tenantId] });
      qc.invalidateQueries({ queryKey: ['inv-outstanding', tenantId] });
      onClose();
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });

  return (
    <Modal open={true} onClose={onClose} title={`Issue: ${item.name}`}>
      <form onSubmit={(e) => { e.preventDefault(); issue.mutate(); }} className="space-y-3">
        <div className="grid grid-cols-2 gap-1 p-1 bg-slate-100 rounded-brand">
          {(['staff', 'student'] as const).map((t) => (
            <button key={t} type="button" onClick={() => setTarget(t)}
              className={`py-1.5 rounded-[calc(var(--brand-radius)-2px)] text-sm font-medium transition ${target === t ? 'bg-white shadow-sm' : 'text-slate-600'}`}>
              {t === 'staff' ? 'Staff' : 'Student'}
            </button>
          ))}
        </div>
        {target === 'staff' ? (
          <StaffPicker tenantId={tenantId} value={staffId} onChange={setStaffId} required />
        ) : (
          <StudentPicker tenantId={tenantId} value={studentId} onChange={setStudentId} required />
        )}
        <Input type="datetime-local" label="Expected return (optional)" value={expected}
               onChange={(e) => setExpected(e.target.value)} />
        <Input label="Notes (optional)" value={notes}
               onChange={(e) => setNotes(e.target.value)} />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={issue.isPending}
                  disabled={target === 'staff' ? !staffId : !studentId}>
            Issue
          </Button>
        </div>
      </form>
    </Modal>
  );
}
