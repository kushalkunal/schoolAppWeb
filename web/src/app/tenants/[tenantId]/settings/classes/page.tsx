'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, UserCheck, X } from 'lucide-react';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { SectionResponse, StaffResponse } from '@/types/domain';

interface DraftClass { name: string; sections: string[] }

export default function ClassesSettingsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [assignTarget, setAssignTarget] = useState<SectionResponse | null>(null);

  const q = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  // Pre-fetch class-teacher staff list for the assign modal (stale 5 min)
  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const teachers: StaffResponse[] =
    staffQ.data?.filter((s) => s.role === 'CLASS_TEACHER' && s.active) ?? [];

  // Build staffId → displayName map so section cards can show assigned teacher name
  const staffById = new Map<string, string>(
    staffQ.data?.map((s) => [s.id, s.displayName]) ?? [],
  );

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Classes &amp; sections</h1>
          <p className="text-sm text-slate-500">
            Manage the class catalog and assign class teachers to sections.
          </p>
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
                <div className="space-y-2">
                  {c.sections.map((s) => (
                    <div key={s.id} className="flex items-center justify-between gap-2 text-sm">
                      <div>
                        <span className="font-medium">{c.name} · {s.name}</span>
                        {s.classTeacherId ? (
                          <div className="text-xs text-green-700 flex items-center gap-1 mt-0.5">
                            <UserCheck size={11} />
                            {staffById.get(s.classTeacherId) ?? 'Assigned'}
                          </div>
                        ) : (
                          <div className="text-xs text-slate-400 mt-0.5">No class teacher</div>
                        )}
                      </div>
                      <RequireRole roles={OWNER_OR_ADMIN}>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setAssignTarget(s)}
                          title="Assign class teacher"
                        >
                          <UserCheck size={14} />
                        </Button>
                      </RequireRole>
                    </div>
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

      {assignTarget && (
        <AssignTeacherModal
          tenantId={tenantId}
          section={assignTarget}
          teachers={teachers}
          onClose={() => setAssignTarget(null)}
          onAssigned={() => {
            setAssignTarget(null);
            qc.invalidateQueries({ queryKey: ['classes', tenantId] });
          }}
        />
      )}
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

// ---------------------------------------------------------------------------
// Assign class teacher modal
// ---------------------------------------------------------------------------
function AssignTeacherModal({
  tenantId,
  section,
  teachers,
  onClose,
  onAssigned,
}: {
  tenantId: string;
  section: SectionResponse;
  teachers: StaffResponse[];
  onClose: () => void;
  onAssigned: () => void;
}) {
  const [staffId, setStaffId] = useState(section.classTeacherId ?? '');

  const assign = useMutation({
    mutationFn: () =>
      schoolApi.assignClassTeacher(tenantId, section.id, { staffId }),
    onSuccess: onAssigned,
  });

  return (
    <Modal open onClose={onClose} title="Assign class teacher">
      <div className="space-y-4">
        {assign.isError && <ErrorBanner error={assign.error} />}

        {teachers.length === 0 ? (
          <p className="text-sm text-slate-500">
            No active CLASS_TEACHER staff found. Go to{' '}
            <a href={`/tenants/${tenantId}/settings/staff`} className="text-primary underline">
              Settings → Staff
            </a>{' '}
            and add a staff member with the Class Teacher role first.
          </p>
        ) : (
          <div>
            <label className="text-sm text-slate-700 mb-1 block">Select teacher</label>
            <select
              value={staffId}
              onChange={(e) => setStaffId(e.target.value)}
              className="block w-full rounded border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">— unassign —</option>
              {teachers.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.displayName}
                  {t.phone ? ` · ${t.phone}` : ''}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" type="button" onClick={onClose} disabled={assign.isPending}>
            Cancel
          </Button>
          <Button
            type="button"
            onClick={() => assign.mutate()}
            disabled={assign.isPending || !staffId || teachers.length === 0}
          >
            {assign.isPending ? <><Spinner className="mr-2" /> Saving…</> : 'Assign'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
