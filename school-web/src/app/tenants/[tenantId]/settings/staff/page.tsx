'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, Trash2 } from 'lucide-react';
import { schoolApi } from '@/api/endpoints/school';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { CreateStaffRequest } from '@/types/domain';
import type { StaffRole } from '@/auth/jwt';

const ASSIGNABLE_ROLES: StaffRole[] = [
  'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER', 'ACCOUNTANT', 'RECEPTIONIST', 'VIEWER',
];

export default function StaffSettingsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
  });

  const deactivate = useMutation({
    mutationFn: (staffId: string) => schoolApi.deactivateStaff(tenantId, staffId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['staff', tenantId] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Staff</h1>
          <p className="text-sm text-slate-500">New hires get a WhatsApp welcome with login instructions.</p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setOpen(true)}><Plus size={16} className="mr-1" /> Add staff</Button>
        </RequireRole>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}
      {deactivate.isError && <ErrorBanner error={deactivate.error} />}

      {q.data && q.data.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Name</th>
                <th className="text-left px-4 py-2 font-medium">Role</th>
                <th className="text-left px-4 py-2 font-medium">Phone</th>
                <th className="text-left px-4 py-2 font-medium">Email</th>
                <th className="text-right px-4 py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((s) => (
                <tr key={s.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium">{s.displayName}</td>
                  <td className="px-4 py-2 text-slate-600">{prettyRole(s.role)}</td>
                  <td className="px-4 py-2 text-slate-600">{s.phone ?? '—'}</td>
                  <td className="px-4 py-2 text-slate-600">{s.email ?? '—'}</td>
                  <td className="px-4 py-2 text-right">
                    <RequireRole roles={['SCHOOL_OWNER', 'PRINCIPAL']}>
                      {s.role !== 'PRINCIPAL' && s.active && (
                        <Button variant="ghost" size="sm" onClick={() => {
                          if (confirm(`Deactivate ${s.displayName}? They lose login access.`)) {
                            deactivate.mutate(s.id);
                          }
                        }}>
                          <Trash2 size={14} />
                        </Button>
                      )}
                    </RequireRole>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AddStaffModal open={open} tenantId={tenantId} onClose={() => setOpen(false)} onCreated={() => {
        setOpen(false);
        qc.invalidateQueries({ queryKey: ['staff', tenantId] });
      }} />
    </div>
  );
}

function AddStaffModal({ open, tenantId, onClose, onCreated }: {
  open: boolean; tenantId: string; onClose: () => void; onCreated: () => void;
}) {
  const [form, setForm] = useState<CreateStaffRequest>({
    firstName: '', phone: '', role: 'ADMIN',
  });

  const create = useMutation({
    mutationFn: () => schoolApi.createStaff(tenantId, form),
    onSuccess: onCreated,
  });

  return (
    <Modal open={open} onClose={onClose} title="Add staff member">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        {create.isError && <ErrorBanner error={create.error} />}
        <Input label="First name" value={form.firstName} required
          onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
        <Input label="Last name" value={form.lastName ?? ''}
          onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
        <Input label="Phone (WhatsApp-capable)" value={form.phone} required type="tel" inputMode="numeric"
          placeholder="9876543210"
          onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        <Input label="Email (optional)" value={form.email ?? ''} type="email"
          onChange={(e) => setForm({ ...form, email: e.target.value })} />
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Role</span>
          <select
            value={form.role}
            onChange={(e) => setForm({ ...form, role: e.target.value as StaffRole })}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            {ASSIGNABLE_ROLES.map((r) => <option key={r} value={r}>{prettyRole(r)}</option>)}
          </select>
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || !form.firstName || !form.phone}>
            {create.isPending ? 'Creating…' : 'Create + send invite'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** Pretty-print a role enum ("SUBJECT_TEACHER" → "Subject Teacher"). */
function prettyRole(role: string): string {
  return role.replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase());
}
