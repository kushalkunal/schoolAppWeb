'use client';

/**
 * Front-desk visitor log. List shows open (still-in-premises) visitors at the top with a
 * "Check out" button, then a paginated history below. New-visitor button opens a quick form.
 * Gated globally by VISITOR_MANAGEMENT + role OWNER_OR_ADMIN.
 */
import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UserPlus, LogOut } from 'lucide-react';
import { visitorApi, type CreateVisitorRequest } from '@/api/endpoints/dailyOps';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { Badge } from '@/components/ui/Badge';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { isFeatureEnabled } from '@/features/featureFlags';

export default function VisitorsPage() {
  if (!isFeatureEnabled('VISITOR_MANAGEMENT')) {
    return <EmptyState title="Visitor management is disabled" description="Set NEXT_PUBLIC_FEATURE_VISITOR_MANAGEMENT=true to enable." />;
  }
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const openQ = useQuery({
    queryKey: ['visitors-open', tenantId],
    queryFn: () => visitorApi.open(tenantId),
    enabled: !!tenantId,
    refetchInterval: 30_000,
  });
  const historyQ = useQuery({
    queryKey: ['visitors-history', tenantId],
    queryFn: () => visitorApi.list(tenantId, 0, 100),
    enabled: !!tenantId,
  });

  const checkOutMutation = useMutation({
    mutationFn: (id: string) => visitorApi.checkOut(tenantId, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['visitors-open', tenantId] });
      qc.invalidateQueries({ queryKey: ['visitors-history', tenantId] });
    },
  });

  return (
    <div className="space-y-4">
      <PageHeader
        title="Visitors"
        description="Gate log — who is in the school right now and when did they arrive."
        actions={<Button onClick={() => setOpen(true)}><UserPlus size={14} className="mr-1" /> New visitor</Button>}
      />

      <Card>
        <CardBody>
          <h2 className="text-sm font-semibold mb-3">Currently on premises</h2>
          {openQ.isLoading ? <Spinner /> :
            (openQ.data ?? []).length === 0 ? (
              <p className="text-sm text-slate-500">No one is checked in right now.</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-xs text-slate-500 text-left">
                  <tr><th className="py-1">Name</th><th>Purpose</th><th>Phone</th><th>In at</th><th></th></tr>
                </thead>
                <tbody>
                  {(openQ.data ?? []).map((v) => (
                    <tr key={v.id} className="border-t border-slate-100">
                      <td className="py-1.5 font-medium">{v.name} {v.badgeNumber && <Badge tone="primary" size="sm">#{v.badgeNumber}</Badge>}</td>
                      <td>{v.purpose ?? '—'}</td>
                      <td>{v.phone ?? '—'}</td>
                      <td>{new Date(v.inAt).toLocaleString()}</td>
                      <td className="text-right">
                        <Button variant="ghost" onClick={() => checkOutMutation.mutate(v.id)}>
                          <LogOut size={12} className="mr-1" /> Check out
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <h2 className="text-sm font-semibold mb-3">Recent history</h2>
          {historyQ.isLoading ? <Spinner /> :
            (historyQ.data?.content ?? []).length === 0 ? (
              <p className="text-sm text-slate-500">No visitors logged yet.</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-xs text-slate-500 text-left">
                  <tr><th className="py-1">Name</th><th>Purpose</th><th>In</th><th>Out</th></tr>
                </thead>
                <tbody>
                  {(historyQ.data?.content ?? []).map((v) => (
                    <tr key={v.id} className="border-t border-slate-100">
                      <td className="py-1.5">{v.name}</td>
                      <td>{v.purpose ?? '—'}</td>
                      <td>{new Date(v.inAt).toLocaleString()}</td>
                      <td>{v.outAt ? new Date(v.outAt).toLocaleString() : <Badge tone="warning" size="sm">Open</Badge>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
        </CardBody>
      </Card>

      {open && <VisitorForm tenantId={tenantId} onClose={() => setOpen(false)} onSaved={() => {
        setOpen(false);
        qc.invalidateQueries({ queryKey: ['visitors-open', tenantId] });
      }} />}
    </div>
  );
}

function VisitorForm({ tenantId, onClose, onSaved }: { tenantId: string; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<CreateVisitorRequest>({ name: '' });
  const mut = useMutation({
    mutationFn: () => visitorApi.checkIn(tenantId, form),
    onSuccess: onSaved,
  });
  return (
    <Modal open onClose={onClose} title="Check in visitor">
      <div className="space-y-3">
        <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <Input label="Phone" value={form.phone ?? ''} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        <Input label="Purpose" value={form.purpose ?? ''} onChange={(e) => setForm({ ...form, purpose: e.target.value })} />
        <Input label="Badge number" value={form.badgeNumber ?? ''} onChange={(e) => setForm({ ...form, badgeNumber: e.target.value })} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button onClick={() => mut.mutate()} disabled={!form.name.trim() || mut.isPending}>
            {mut.isPending ? 'Saving…' : 'Check in'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
