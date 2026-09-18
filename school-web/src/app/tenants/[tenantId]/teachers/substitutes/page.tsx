'use client';

/**
 * Teachers → Substitutes page
 * Admin assigns a substitute teacher when a class teacher is absent.
 * Shows today's assignments (with ability to cancel) and a form to add a new one.
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { UserCog, Plus, Trash2, AlertTriangle } from 'lucide-react';
import { schoolApi } from '@/api/endpoints/school';
import { substitutesApi } from '@/api/endpoints/substitutes';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Badge } from '@/components/ui/Badge';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { todayIso } from '@/lib/utils';
import type {
  ClassResponse,
  SectionResponse,
  StaffResponse,
  SubstituteResponse,
} from '@/types/domain';

export default function SubstitutesPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const today = todayIso();

  const [assignOpen, setAssignOpen] = useState(false);
  const [selectedDate, setSelectedDate] = useState(today);

  // Form state
  const [absentTeacherId, setAbsentTeacherId] = useState('');
  const [substituteId, setSubstituteId] = useState('');
  const [sectionId, setSectionId] = useState('');
  const [note, setNote] = useState('');
  const [formError, setFormError] = useState('');

  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const substitutesQ = useQuery<SubstituteResponse[]>({
    queryKey: ['substitutes', tenantId, selectedDate],
    queryFn: () => substitutesApi.listForDate(tenantId, selectedDate),
    enabled: !!tenantId,
  });

  const teachers = staffQ.data?.filter(
    (s) => s.active && (s.role === 'CLASS_TEACHER' || s.role === 'SUBJECT_TEACHER'),
  ) ?? [];

  const staffById = new Map<string, StaffResponse>(
    staffQ.data?.map((s) => [s.id, s]) ?? []
  );

  const sectionLabel = new Map<string, string>();
  classesQ.data?.forEach((c: ClassResponse) => {
    c.sections.forEach((s: SectionResponse) => {
      sectionLabel.set(s.id, `${c.name} – ${s.name}`);
    });
  });

  const allSections: { id: string; label: string }[] = [];
  classesQ.data?.forEach((c: ClassResponse) => {
    c.sections.forEach((s: SectionResponse) => {
      allSections.push({ id: s.id, label: `${c.name} – ${s.name}` });
    });
  });

  const assign = useMutation({
    mutationFn: () =>
      substitutesApi.assign(tenantId, {
        absentTeacherId,
        substituteId,
        sectionId,
        assignedDate: selectedDate,
        note: note || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['substitutes', tenantId] });
      setAssignOpen(false);
      setAbsentTeacherId('');
      setSubstituteId('');
      setSectionId('');
      setNote('');
      setFormError('');
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : 'Could not assign substitute.';
      setFormError(msg);
    },
  });

  const cancel = useMutation({
    mutationFn: (id: string) => substitutesApi.cancel(tenantId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['substitutes', tenantId] }),
  });

  const handleAssign = () => {
    setFormError('');
    if (!absentTeacherId) { setFormError('Select the absent teacher.'); return; }
    if (!substituteId)    { setFormError('Select a substitute.'); return; }
    if (!sectionId)       { setFormError('Select a section.'); return; }
    if (absentTeacherId === substituteId) {
      setFormError('Absent teacher and substitute cannot be the same person.');
      return;
    }
    assign.mutate();
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Substitute Teachers</h1>
          <p className="text-sm text-slate-500 mt-1">
            Assign a substitute when a class teacher is absent.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setAssignOpen(true)}>
            <Plus size={16} className="mr-1" /> Assign Substitute
          </Button>
        </RequireRole>
      </div>

      {/* Date picker */}
      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-slate-700">Date:</label>
        <input
          type="date"
          value={selectedDate}
          onChange={(e) => setSelectedDate(e.target.value)}
          className="rounded border border-slate-300 px-3 py-1.5 text-sm"
        />
        {selectedDate !== today && (
          <button
            onClick={() => setSelectedDate(today)}
            className="text-xs text-primary hover:underline"
          >
            Back to today
          </button>
        )}
      </div>

      {/* List */}
      {substitutesQ.isLoading && <Spinner />}
      {substitutesQ.isError && <ErrorBanner error={substitutesQ.error} onRetry={() => substitutesQ.refetch()} />}
      {substitutesQ.data && substitutesQ.data.length === 0 && (
        <div className="text-center py-12 bg-white border border-slate-200 rounded-lg">
          <UserCog size={32} className="mx-auto text-slate-300 mb-3" />
          <p className="text-sm font-medium text-slate-600">No substitutes assigned for this date</p>
          <p className="text-xs text-slate-400 mt-1">Use the &quot;Assign Substitute&quot; button to create one.</p>
        </div>
      )}
      {substitutesQ.data && substitutesQ.data.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Section</th>
                <th className="text-left px-4 py-2 font-medium">Absent Teacher</th>
                <th className="text-left px-4 py-2 font-medium">Substitute</th>
                <th className="text-left px-4 py-2 font-medium">Note</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {substitutesQ.data.map((s: SubstituteResponse) => (
                <tr key={s.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium">
                    {sectionLabel.get(s.sectionId) ?? s.sectionId}
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <AlertTriangle size={12} className="text-warning" />
                      {staffById.get(s.absentTeacherId)?.displayName ?? '—'}
                    </div>
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <Badge tone="info" size="sm">Sub</Badge>
                      {staffById.get(s.substituteId)?.displayName ?? '—'}
                    </div>
                  </td>
                  <td className="px-4 py-2 text-slate-500">{s.note ?? '—'}</td>
                  <td className="px-4 py-2 text-right">
                    <RequireRole roles={OWNER_OR_ADMIN}>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => cancel.mutate(s.id)}
                        disabled={cancel.isPending}
                        title="Cancel substitute assignment"
                      >
                        <Trash2 size={14} />
                      </Button>
                    </RequireRole>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Assign modal */}
      <Modal
        open={assignOpen}
        onClose={() => { setAssignOpen(false); setFormError(''); }}
        title="Assign Substitute Teacher"
      >
        <div className="space-y-4 p-1">
          {formError && (
            <div className="rounded border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
              {formError}
            </div>
          )}

          <div className="space-y-1">
            <label className="text-sm font-medium text-slate-700">Date</label>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
            />
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium text-slate-700">Section</label>
            <select
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              className="w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
            >
              <option value="">— select section —</option>
              {allSections.map((s) => (
                <option key={s.id} value={s.id}>{s.label}</option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium text-slate-700">Absent Teacher</label>
            <select
              value={absentTeacherId}
              onChange={(e) => setAbsentTeacherId(e.target.value)}
              className="w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
            >
              <option value="">— select absent teacher —</option>
              {teachers.map((t) => (
                <option key={t.id} value={t.id}>{t.displayName}</option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium text-slate-700">Substitute Teacher</label>
            <select
              value={substituteId}
              onChange={(e) => setSubstituteId(e.target.value)}
              className="w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
            >
              <option value="">— select substitute —</option>
              {teachers
                .filter((t) => t.id !== absentTeacherId)
                .map((t) => (
                  <option key={t.id} value={t.id}>{t.displayName}</option>
                ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium text-slate-700">Note (optional)</label>
            <input
              type="text"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. Sick leave, personal"
              className="w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => { setAssignOpen(false); setFormError(''); }}>
              Cancel
            </Button>
            <Button onClick={handleAssign} disabled={assign.isPending}>
              {assign.isPending ? 'Assigning…' : 'Assign'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
