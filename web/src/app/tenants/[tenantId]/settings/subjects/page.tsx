'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, X, BookOpen } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

interface DraftSubject { name: string; code: string }

export default function SubjectsSettingsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Subjects</h1>
          <p className="text-sm text-slate-500">
            Subjects appear as columns in the marks-entry grid for every exam. Adding a subject after marks are entered
            simply adds another column to fill — existing marks stay untouched.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setOpen(true)}><Plus size={16} className="mr-1" /> Add subjects</Button>
        </RequireRole>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && q.data.length === 0 && (
        <Card>
          <div className="text-center py-8 text-slate-500">
            <BookOpen className="mx-auto mb-2" size={32} />
            <p>No subjects yet. Add them before creating an exam.</p>
          </div>
        </Card>
      )}

      {q.data && q.data.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Subject</th>
                <th className="text-left px-4 py-2 font-medium">Code</th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((s) => (
                <tr key={s.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium">{s.name}</td>
                  <td className="px-4 py-2 font-mono text-xs text-slate-600">{s.code ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AddSubjectsModal
        open={open}
        tenantId={tenantId}
        onClose={() => setOpen(false)}
        onSuccess={() => { setOpen(false); qc.invalidateQueries({ queryKey: ['subjects', tenantId] }); }}
      />
    </div>
  );
}

const SUGGESTIONS: DraftSubject[] = [
  { name: 'English', code: 'ENG' },
  { name: 'Mathematics', code: 'MATH' },
  { name: 'Science', code: 'SCI' },
  { name: 'Social Studies', code: 'SST' },
  { name: 'Hindi', code: 'HIN' },
  { name: 'Computer', code: 'CS' },
];

function AddSubjectsModal({ open, tenantId, onClose, onSuccess }: {
  open: boolean; tenantId: string; onClose: () => void; onSuccess: () => void;
}) {
  const [drafts, setDrafts] = useState<DraftSubject[]>([{ name: '', code: '' }]);

  const create = useMutation({
    mutationFn: () => academicsApi.bulkCreateSubjects(tenantId, {
      subjects: drafts
        .filter((d) => d.name.trim().length > 0)
        .map((d) => ({ name: d.name.trim(), code: d.code.trim() || undefined })),
    }),
    onSuccess,
  });

  const updateDraft = (idx: number, patch: Partial<DraftSubject>) => {
    setDrafts((ds) => ds.map((d, i) => i === idx ? { ...d, ...patch } : d));
  };
  const addDraft = () => setDrafts((ds) => [...ds, { name: '', code: '' }]);
  const removeDraft = (idx: number) => setDrafts((ds) => ds.filter((_, i) => i !== idx));

  const seedCommon = () => setDrafts(SUGGESTIONS);

  const validCount = drafts.filter((d) => d.name.trim().length > 0).length;

  return (
    <Modal open={open} onClose={onClose} title="Add subjects">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        {create.isError && <ErrorBanner error={create.error} />}

        <div className="text-sm text-slate-600 flex items-center justify-between">
          <span>One row per subject. Code is optional (e.g. ENG, MATH).</span>
          <button type="button" onClick={seedCommon} className="text-xs text-primary hover:underline">
            Fill common subjects
          </button>
        </div>

        <div className="space-y-2">
          {drafts.map((d, idx) => (
            <div key={idx} className="flex items-center gap-2">
              <div className="flex-1">
                <Input
                  value={d.name}
                  required={idx === 0 && drafts.length === 1}
                  maxLength={100}
                  placeholder="Subject name (e.g. Mathematics)"
                  onChange={(e) => updateDraft(idx, { name: e.target.value })}
                />
              </div>
              <div className="w-28">
                <Input
                  value={d.code}
                  maxLength={20}
                  placeholder="MATH"
                  onChange={(e) => updateDraft(idx, { code: e.target.value.toUpperCase() })}
                />
              </div>
              <button
                type="button"
                onClick={() => removeDraft(idx)}
                disabled={drafts.length === 1}
                className="text-slate-400 hover:text-red-600 disabled:opacity-30 disabled:cursor-not-allowed p-1"
                title="Remove"
              >
                <X size={16} />
              </button>
            </div>
          ))}
        </div>

        <button type="button" onClick={addDraft} className="text-sm text-primary hover:underline">
          + Add another
        </button>

        <p className="text-xs text-slate-500">
          Duplicate names are silently skipped — safe to re-submit.
        </p>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || validCount === 0}>
            {create.isPending ? 'Saving…' : `Create ${validCount} subject${validCount === 1 ? '' : 's'}`}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
