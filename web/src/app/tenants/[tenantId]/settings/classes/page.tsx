'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, X } from 'lucide-react';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

interface DraftClass { name: string; sections: string[] }

export default function ClassesSettingsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Classes &amp; sections</h1>
          <p className="text-sm text-slate-500">The class catalog for the current academic year.</p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setOpen(true)}>
            <Plus size={16} className="mr-1" /> Add classes
          </Button>
        </RequireRole>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && q.data.length === 0 && (
        <div className="bg-white border border-slate-200 rounded-lg p-10 text-center text-slate-500">
          No classes yet. Add some — students get bound to a section on creation.
        </div>
      )}

      {q.data && q.data.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {q.data.map((c) => (
            <Card key={c.id}>
              <CardHeader><CardTitle>{c.name}</CardTitle></CardHeader>
              <CardBody>
                <div className="flex flex-wrap gap-1">
                  {c.sections.map((s) => (
                    <span key={s.id} className="text-xs bg-slate-100 px-2 py-0.5 rounded">{s.name}</span>
                  ))}
                </div>
              </CardBody>
            </Card>
          ))}
        </div>
      )}

      <AddClassesModal
        open={open}
        tenantId={tenantId}
        onClose={() => setOpen(false)}
        onCreated={() => {
          setOpen(false);
          qc.invalidateQueries({ queryKey: ['classes', tenantId] });
        }}
      />
    </div>
  );
}

function AddClassesModal({ open, tenantId, onClose, onCreated }: {
  open: boolean; tenantId: string; onClose: () => void; onCreated: () => void;
}) {
  const [drafts, setDrafts] = useState<DraftClass[]>([{ name: '', sections: ['A'] }]);

  const create = useMutation({
    mutationFn: () => schoolApi.createClasses(tenantId, {
      classes: drafts
        .filter((c) => c.name.trim() && c.sections.some((s) => s.trim()))
        .map((c) => ({ name: c.name.trim(), sections: c.sections.map((s) => s.trim()).filter(Boolean) })),
    }),
    onSuccess: onCreated,
  });

  function updateDraft(i: number, patch: Partial<DraftClass>) {
    setDrafts((ds) => ds.map((d, idx) => idx === i ? { ...d, ...patch } : d));
  }

  return (
    <Modal open={open} onClose={onClose} title="Add classes">
      <div className="space-y-3">
        {create.isError && <ErrorBanner error={create.error} />}

        {drafts.map((d, i) => (
          <Card key={i}>
            <CardBody className="space-y-2">
              <div className="flex gap-2 items-end">
                <Input
                  label="Class name"
                  value={d.name}
                  onChange={(e) => updateDraft(i, { name: e.target.value })}
                  placeholder="Class 5"
                  className="flex-1"
                />
                {drafts.length > 1 && (
                  <Button variant="ghost" size="sm"
                    onClick={() => setDrafts((ds) => ds.filter((_, idx) => idx !== i))}
                  >
                    <X size={16} />
                  </Button>
                )}
              </div>
              <div>
                <span className="text-sm text-slate-700 mb-1 inline-block">Sections (comma-separated)</span>
                <input
                  value={d.sections.join(',')}
                  onChange={(e) => updateDraft(i, { sections: e.target.value.split(',').map((s) => s.trim()) })}
                  placeholder="A, B, C"
                  className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            </CardBody>
          </Card>
        ))}

        <Button variant="ghost" size="sm" onClick={() => setDrafts((ds) => [...ds, { name: '', sections: ['A'] }])}>
          + Add another class
        </Button>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button onClick={() => create.mutate()} disabled={create.isPending}>
            {create.isPending ? <><Spinner className="mr-2" /> Saving…</> : 'Create'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
