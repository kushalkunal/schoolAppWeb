'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { expenseApi, type CreateExpenseRequest } from '@/api/endpoints/dailyOps';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { OWNER_OR_ADMIN, FEE_WRITER, RequireRole } from '@/auth/RequireRole';
import { formatINR } from '@/lib/utils';
import { isFeatureEnabled } from '@/features/featureFlags';

export default function ExpensesPage() {
  if (!isFeatureEnabled('EXPENSE_TRACKING')) {
    return <EmptyState title="Expense tracking is disabled" />;
  }
  return <RequireRole roles={FEE_WRITER}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const listQ = useQuery({
    queryKey: ['expenses', tenantId],
    queryFn: () => expenseApi.list(tenantId, 0, 100),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-4">
      <PageHeader title="Expenses" description="Per-tenant operational expense log."
        actions={<Button onClick={() => setOpen(true)}><Plus size={14} className="mr-1" /> Add expense</Button>} />

      <Card>
        <CardBody>
          {listQ.isLoading ? <Spinner /> : (listQ.data?.content ?? []).length === 0 ? (
            <p className="text-sm text-slate-500">No expenses recorded yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-xs text-slate-500 text-left">
                <tr><th>Date</th><th>Vendor</th><th>Description</th><th>Mode</th><th className="text-right">Amount</th><th></th></tr>
              </thead>
              <tbody>
                {(listQ.data?.content ?? []).map((e) => (
                  <tr key={e.id} className="border-t border-slate-100">
                    <td className="py-1.5">{e.spentOn}</td>
                    <td>{e.vendor ?? '—'}</td>
                    <td>{e.description ?? '—'}</td>
                    <td>{e.paymentMode ?? '—'}</td>
                    <td className="text-right font-mono">{formatINR(e.amountPaise)}</td>
                    <td className="text-right">
                      <RequireRole roles={OWNER_OR_ADMIN}>
                        <button className="text-rose-600 hover:underline text-xs"
                          onClick={async () => { await expenseApi.delete(tenantId, e.id); qc.invalidateQueries({ queryKey: ['expenses', tenantId] }); }}>
                          <Trash2 size={12} className="inline" /> delete
                        </button>
                      </RequireRole>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>

      {open && <ExpenseForm tenantId={tenantId} onClose={() => setOpen(false)} onSaved={() => {
        setOpen(false); qc.invalidateQueries({ queryKey: ['expenses', tenantId] });
      }} />}
    </div>
  );
}

function ExpenseForm({ tenantId, onClose, onSaved }: { tenantId: string; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<CreateExpenseRequest>({ amountPaise: 0, spentOn: new Date().toISOString().slice(0, 10) });
  const mut = useMutation({ mutationFn: () => expenseApi.create(tenantId, form), onSuccess: onSaved });
  return (
    <Modal open onClose={onClose} title="Add expense">
      <div className="space-y-3">
        <Input label="Vendor" value={form.vendor ?? ''} onChange={(e) => setForm({ ...form, vendor: e.target.value })} />
        <Input label="Description" value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <Input label="Amount (₹)" type="number" min={0} step="0.01"
          value={form.amountPaise / 100 || ''}
          onChange={(e) => setForm({ ...form, amountPaise: Math.round(Number(e.target.value) * 100) })} />
        <Input label="Spent on" type="date" value={form.spentOn} onChange={(e) => setForm({ ...form, spentOn: e.target.value })} />
        <label className="block">
          <span className="text-sm text-slate-700">Payment mode</span>
          <select className="mt-1 block w-full border border-slate-200 rounded px-3 py-2 text-sm"
            value={form.paymentMode ?? ''}
            onChange={(e) => setForm({ ...form, paymentMode: e.target.value })}>
            <option value="">— select —</option>
            <option value="CASH">Cash</option>
            <option value="UPI">UPI</option>
            <option value="CHEQUE">Cheque</option>
            <option value="BANK">Bank transfer</option>
            <option value="CARD">Card</option>
          </select>
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button onClick={() => mut.mutate()} disabled={mut.isPending || form.amountPaise <= 0}>
            {mut.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
