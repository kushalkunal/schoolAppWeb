'use client';

/**
 * Fee Reminder Schedules — Slice 35.
 *
 * Lets OWNER/ADMIN configure when automatic payment reminders are sent to parents.
 * Rules map to {@code fee_reminder_schedules} rows; the cron at 09:30 IST
 * fires them daily against outstanding invoices.
 *
 * Trigger types:
 *  - BEFORE_DUE  → N days before the invoice due date
 *  - ON_DUE      → on the due date (daysOffset must be 0)
 *  - AFTER_DUE   → N days after the due date (escalation)
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Bell, Plus, Trash2, Pencil, CheckCircle2, XCircle } from 'lucide-react';
import { feeRemindersApi, type FeeReminderSchedule } from '@/api/endpoints/remindersAndTemplates';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole, useHasRole } from '@/auth/RequireRole';

const TRIGGER_LABELS: Record<FeeReminderSchedule['triggerType'], string> = {
  BEFORE_DUE: 'days before due',
  ON_DUE: 'on due date',
  AFTER_DUE: 'days after due',
};

const BLANK: Omit<FeeReminderSchedule, 'id'> = {
  name: '',
  triggerType: 'BEFORE_DUE',
  daysOffset: 3,
  includeUpiLink: true,
  active: true,
};

function describeSchedule(s: FeeReminderSchedule) {
  if (s.triggerType === 'ON_DUE') return 'On the due date';
  return `${s.daysOffset} ${TRIGGER_LABELS[s.triggerType]}`;
}

export default function FeeRemindersPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <RemindersContent />
    </RequireRole>
  );
}

function RemindersContent() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const { showToast } = useToast();
  const canEdit = useHasRole(...OWNER_OR_ADMIN);

  const [editing, setEditing] = useState<FeeReminderSchedule | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<Omit<FeeReminderSchedule, 'id'>>(BLANK);

  const q = useQuery({
    queryKey: ['fee-reminder-schedules', tenantId],
    queryFn: () => feeRemindersApi.list(tenantId),
    enabled: !!tenantId,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['fee-reminder-schedules', tenantId] });

  const createMut = useMutation({
    mutationFn: (body: typeof BLANK) => feeRemindersApi.create(tenantId, body),
    onSuccess: () => { invalidate(); setShowForm(false); setForm(BLANK); showToast('Reminder schedule created', 'success'); },
    onError: () => showToast('Failed to create schedule', 'error'),
  });

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: typeof BLANK }) =>
      feeRemindersApi.update(tenantId, id, body),
    onSuccess: () => { invalidate(); setEditing(null); showToast('Schedule updated', 'success'); },
    onError: () => showToast('Failed to update schedule', 'error'),
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => feeRemindersApi.remove(tenantId, id),
    onSuccess: () => { invalidate(); showToast('Schedule removed', 'success'); },
    onError: () => showToast('Failed to remove schedule', 'error'),
  });

  function openEdit(s: FeeReminderSchedule) {
    setEditing(s);
    setForm({ name: s.name, triggerType: s.triggerType, daysOffset: s.daysOffset,
      includeUpiLink: s.includeUpiLink, active: s.active });
    setShowForm(false);
  }

  function openNew() { setEditing(null); setForm(BLANK); setShowForm(true); }

  function submit() {
    if (!form.name.trim()) return;
    if (editing) updateMut.mutate({ id: editing.id, body: form });
    else createMut.mutate(form);
  }

  const busy = createMut.isPending || updateMut.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fee Reminder Schedules"
        subtitle="Automatic WhatsApp reminders are sent to parents based on these rules. The system checks daily at 09:30."
        icon={Bell}
        actions={canEdit ? <Button size="sm" onClick={openNew}><Plus size={14} className="mr-1" />New Rule</Button> : undefined}
      />

      {q.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner />Loading…</div>}
      {q.isError && <ErrorBanner error={q.error} onRetry={q.refetch} />}

      {/* Form */}
      {(showForm || editing !== null) && (
        <Card>
          <CardHeader>
            <CardTitle>{editing ? 'Edit Rule' : 'New Reminder Rule'}</CardTitle>
          </CardHeader>
          <CardBody className="space-y-4 max-w-lg">
            <div>
              <label className="block text-sm font-medium mb-1">Rule name</label>
              <input
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                placeholder="e.g. 3 days before due"
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">When to fire</label>
                <select
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  value={form.triggerType}
                  onChange={e => setForm(f => ({
                    ...f,
                    triggerType: e.target.value as FeeReminderSchedule['triggerType'],
                    daysOffset: e.target.value === 'ON_DUE' ? 0 : f.daysOffset,
                  }))}
                >
                  <option value="BEFORE_DUE">Days before due date</option>
                  <option value="ON_DUE">On the due date</option>
                  <option value="AFTER_DUE">Days after due date</option>
                </select>
              </div>
              {form.triggerType !== 'ON_DUE' && (
                <div>
                  <label className="block text-sm font-medium mb-1">Days offset</label>
                  <input
                    type="number" min={1} max={30}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                    value={form.daysOffset}
                    onChange={e => setForm(f => ({ ...f, daysOffset: Number(e.target.value) }))}
                  />
                </div>
              )}
            </div>
            <div className="flex items-center gap-6">
              <label className="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" checked={form.includeUpiLink}
                  onChange={e => setForm(f => ({ ...f, includeUpiLink: e.target.checked }))} />
                Include payment link
              </label>
              <label className="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" checked={form.active}
                  onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} />
                Active
              </label>
            </div>
            <div className="flex gap-2">
              <Button onClick={submit} disabled={busy || !form.name.trim()}>
                {busy ? <Spinner size="sm" /> : (editing ? 'Save changes' : 'Create rule')}
              </Button>
              <Button variant="ghost" onClick={() => { setShowForm(false); setEditing(null); }}>
                Cancel
              </Button>
            </div>
          </CardBody>
        </Card>
      )}

      {/* List */}
      {q.data && q.data.length === 0 && !showForm && (
        <div className="text-slate-500 text-sm py-8 text-center">
          No reminder rules yet.{canEdit && ' Click "New Rule" to add one.'}
        </div>
      )}

      {q.data && q.data.length > 0 && (
        <Card>
          <CardBody className="p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100">
                <tr className="text-left text-xs text-slate-500 uppercase tracking-wide">
                  <th className="px-4 py-3">Rule name</th>
                  <th className="px-4 py-3">Fires</th>
                  <th className="px-4 py-3">Payment link</th>
                  <th className="px-4 py-3">Status</th>
                  {canEdit && <th className="px-4 py-3" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {q.data.map(s => (
                  <tr key={s.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium">{s.name}</td>
                    <td className="px-4 py-3 text-slate-600">{describeSchedule(s)}</td>
                    <td className="px-4 py-3">
                      {s.includeUpiLink
                        ? <CheckCircle2 size={14} className="text-green-600" />
                        : <XCircle size={14} className="text-slate-300" />}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={s.active ? 'success' : 'neutral'} size="sm">
                        {s.active ? 'Active' : 'Paused'}
                      </Badge>
                    </td>
                    {canEdit && (
                      <td className="px-4 py-3 text-right">
                        <button onClick={() => openEdit(s)}
                          className="p-1 text-slate-400 hover:text-slate-700 rounded">
                          <Pencil size={14} />
                        </button>
                        <button onClick={() => deleteMut.mutate(s.id)}
                          className="p-1 text-slate-400 hover:text-red-600 rounded ml-1">
                          <Trash2 size={14} />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </CardBody>
        </Card>
      )}
    </div>
  );
}
