'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { infirmaryApi, type CreateVisitRequest } from '@/api/endpoints/dailyOps';
import { studentsApi } from '@/api/endpoints/students';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { StudentPicker } from '@/components/pickers/StudentPicker';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { isFeatureEnabled } from '@/features/featureFlags';

export default function InfirmaryPage() {
  if (!isFeatureEnabled('INFIRMARY_LOG')) return <EmptyState title="Infirmary log is disabled" />;
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const q = useQuery({ queryKey: ['infirmary', tenantId], queryFn: () => infirmaryApi.list(tenantId, 0, 100) });
  const studentsQ = useQuery({
    queryKey: ['students-list-for-picker', tenantId],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    staleTime: 60_000,
  });
  const nameOf = (id: string) =>
    studentsQ.data?.items.find((s) => s.id === id)?.displayName ?? id.slice(0, 8) + '…';

  return (
    <div className="space-y-4">
      <PageHeader title="Infirmary" description="Nurse log + parent notification on sent-home."
        actions={<Button onClick={() => setOpen(true)}><Plus size={14} className="mr-1" /> Record visit</Button>} />
      <Card><CardBody>
        {q.isLoading ? <Spinner /> : (q.data?.content ?? []).length === 0 ? (
          <p className="text-sm text-slate-500">No visits logged.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-xs text-slate-500 text-left">
              <tr><th>When</th><th>Student</th><th>Complaint</th><th>Treatment</th><th>Temp</th><th>Status</th></tr>
            </thead>
            <tbody>
              {(q.data?.content ?? []).map(v => (
                <tr key={v.id} className="border-t border-slate-100">
                  <td className="py-1.5">{new Date(v.visitedAt).toLocaleString()}</td>
                  <td className="font-medium">{nameOf(v.studentId)}</td>
                  <td className="max-w-xs truncate">{v.complaint}</td>
                  <td className="max-w-xs truncate">{v.treatment ?? '—'}</td>
                  <td>{v.temperatureC ? `${v.temperatureC}°C` : '—'}</td>
                  <td>{v.sentHome ? <Badge tone="warning" size="sm">Sent home</Badge> : <Badge tone="success" size="sm">Returned to class</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </CardBody></Card>
      {open && <VisitForm tenantId={tenantId} onClose={() => setOpen(false)} onSaved={() => {
        setOpen(false); qc.invalidateQueries({ queryKey: ['infirmary', tenantId] });
      }} />}
    </div>
  );
}

function VisitForm({ tenantId, onClose, onSaved }: { tenantId: string; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<CreateVisitRequest>({ studentId: '', complaint: '', sentHome: false });
  const mut = useMutation({ mutationFn: () => infirmaryApi.record(tenantId, form), onSuccess: onSaved });
  return (
    <Modal open onClose={onClose} title="Record infirmary visit">
      <div className="space-y-3">
        <StudentPicker
          tenantId={tenantId}
          value={form.studentId || null}
          onChange={(id) => setForm({ ...form, studentId: id ?? '' })}
          required
        />
        <Input label="Complaint" value={form.complaint} onChange={(e) => setForm({ ...form, complaint: e.target.value })} />
        <Input label="Treatment" value={form.treatment ?? ''} onChange={(e) => setForm({ ...form, treatment: e.target.value })} />
        <Input label="Medicine given" value={form.medicineGiven ?? ''} onChange={(e) => setForm({ ...form, medicineGiven: e.target.value })} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Temperature (°C)" type="number" step="0.1"
            value={form.temperatureC ?? ''} onChange={(e) => setForm({ ...form, temperatureC: e.target.value ? Number(e.target.value) : undefined })} />
          <Input label="Pulse" type="number"
            value={form.pulse ?? ''} onChange={(e) => setForm({ ...form, pulse: e.target.value ? Number(e.target.value) : undefined })} />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.sentHome} onChange={(e) => setForm({ ...form, sentHome: e.target.checked })} />
          Send home (notifies parent immediately)
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!form.studentId.trim() || !form.complaint.trim() || mut.isPending} onClick={() => mut.mutate()}>
            {mut.isPending ? 'Saving…' : 'Record'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
