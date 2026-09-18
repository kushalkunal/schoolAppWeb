'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { incidentApi, type CreateIncidentRequest, type IncidentResponse } from '@/api/endpoints/dailyOps';
import { studentsApi } from '@/api/endpoints/students';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { Spinner } from '@/components/ui/Spinner';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { StudentPicker } from '@/components/pickers/StudentPicker';
import { OWNER_OR_ADMIN, ANY_TEACHER, RequireRole } from '@/auth/RequireRole';
import { isFeatureEnabled } from '@/features/featureFlags';

const sevTone = (s: string) => s === 'SEVERE' ? 'danger' : s === 'MAJOR' ? 'warning' : 'neutral';

export default function IncidentsPage() {
  if (!isFeatureEnabled('INCIDENT_LOG')) return <EmptyState title="Incident log is disabled" />;
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const q = useQuery({
    queryKey: ['incidents', tenantId],
    queryFn: () => incidentApi.list(tenantId, 0, 100),
  });
  // Resolve studentId → name so the table doesn't show raw UUIDs.
  const studentsQ = useQuery({
    queryKey: ['students-list-for-picker', tenantId],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    staleTime: 60_000,
  });
  const nameOf = (id: string) =>
    studentsQ.data?.items.find((s) => s.id === id)?.displayName ?? id.slice(0, 8) + '…';

  return (
    <div className="space-y-4">
      <PageHeader title="Incidents" description="Behaviour / discipline / merit-demerit log."
        actions={<RequireRole roles={ANY_TEACHER}><Button onClick={() => setOpen(true)}><Plus size={14} className="mr-1" /> Log incident</Button></RequireRole>} />
      <Card><CardBody>
        {q.isLoading ? <Spinner /> : (q.data?.content ?? []).length === 0 ? (
          <p className="text-sm text-slate-500">No incidents recorded.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-xs text-slate-500 text-left">
              <tr><th>Date</th><th>Student</th><th>Type</th><th>Severity</th><th>Points</th><th>Description</th><th>Notified</th></tr>
            </thead>
            <tbody>
              {(q.data?.content ?? []).map((i: IncidentResponse) => (
                <tr key={i.id} className="border-t border-slate-100">
                  <td className="py-1.5">{i.occurredOn}</td>
                  <td className="font-medium">{nameOf(i.studentId)}</td>
                  <td>{i.incidentType}</td>
                  <td><Badge tone={sevTone(i.severity)} size="sm">{i.severity}</Badge></td>
                  <td className={i.points < 0 ? 'text-rose-600' : 'text-emerald-600'}>{i.points > 0 ? '+' : ''}{i.points}</td>
                  <td className="max-w-md truncate">{i.description}</td>
                  <td>{i.parentNotified ? <Badge tone="success" size="sm">Yes</Badge> : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </CardBody></Card>
      {open && <IncidentForm tenantId={tenantId} onClose={() => setOpen(false)} onSaved={() => {
        setOpen(false); qc.invalidateQueries({ queryKey: ['incidents', tenantId] });
      }} />}
    </div>
  );
}

function IncidentForm({ tenantId, onClose, onSaved }: { tenantId: string; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<CreateIncidentRequest>({
    studentId: '', severity: 'MINOR', incidentType: 'DISCIPLINE',
    points: 0, occurredOn: new Date().toISOString().slice(0, 10),
    description: '', notifyParent: false,
  });
  const mut = useMutation({ mutationFn: () => incidentApi.create(tenantId, form), onSuccess: onSaved });
  return (
    <Modal open onClose={onClose} title="Log incident">
      <div className="space-y-3">
        <StudentPicker
          tenantId={tenantId}
          value={form.studentId || null}
          onChange={(id) => setForm({ ...form, studentId: id ?? '' })}
          required
        />
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-sm text-slate-600 block mb-1">Severity</label>
            <select className="w-full border border-slate-200 rounded px-2 py-1.5 text-sm"
              value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value as CreateIncidentRequest['severity'] })}>
              {['MINOR', 'MAJOR', 'SEVERE'].map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div>
            <label className="text-sm text-slate-600 block mb-1">Type</label>
            <select className="w-full border border-slate-200 rounded px-2 py-1.5 text-sm"
              value={form.incidentType} onChange={(e) => setForm({ ...form, incidentType: e.target.value as CreateIncidentRequest['incidentType'] })}>
              {['MERIT', 'DEMERIT', 'DISCIPLINE', 'ACADEMIC'].map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
        </div>
        <Input label="Points (+ merit / − demerit)" type="number" value={form.points} onChange={(e) => setForm({ ...form, points: Number(e.target.value) })} />
        <Input label="Occurred on" type="date" value={form.occurredOn} onChange={(e) => setForm({ ...form, occurredOn: e.target.value })} />
        <div>
          <label className="text-sm text-slate-600 block mb-1">Description</label>
          <textarea rows={3} className="w-full border border-slate-200 rounded p-2 text-sm"
            value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <Input label="Action taken" value={form.actionTaken ?? ''} onChange={(e) => setForm({ ...form, actionTaken: e.target.value })} />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.notifyParent}
            onChange={(e) => setForm({ ...form, notifyParent: e.target.checked })} />
          Notify parent (WA + email)
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!form.studentId.trim() || !form.description.trim() || mut.isPending} onClick={() => mut.mutate()}>
            {mut.isPending ? 'Saving…' : 'Log'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
