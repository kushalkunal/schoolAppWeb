'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardCheck, Plus } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { CreateExamRequest, ExamType } from '@/types/domain';

const EXAM_TYPES: ExamType[] = ['UNIT_TEST', 'TERM', 'ANNUAL', 'MOCK', 'ACTIVITY'];

export default function ExamsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: ['exams', tenantId],
    queryFn: () => academicsApi.listExams(tenantId),
    enabled: !!tenantId,
  });

  const publish = useMutation({
    mutationFn: (examId: string) => academicsApi.publishExam(tenantId, examId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['exams', tenantId] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Exams</h1>
          <p className="text-sm text-slate-500">
            Create assessments, enter marks per section, and publish report cards. Marks lock to read-only once published.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setOpen(true)}><Plus size={16} className="mr-1" /> New exam</Button>
        </RequireRole>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}
      {publish.isError && <ErrorBanner error={publish.error} />}

      {q.data && q.data.length === 0 && (
        <Card>
          <div className="text-center py-8 text-slate-500">
            <ClipboardCheck className="mx-auto mb-2" size={32} />
            <p>No exams created for the current academic year yet.</p>
          </div>
        </Card>
      )}

      {q.data && q.data.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Name</th>
                <th className="text-left px-4 py-2 font-medium">Type</th>
                <th className="text-left px-4 py-2 font-medium">Dates</th>
                <th className="text-left px-4 py-2 font-medium">Status</th>
                <th className="text-right px-4 py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((e) => (
                <tr key={e.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium">{e.name}</td>
                  <td className="px-4 py-2 text-slate-600">{e.examType.replace('_', ' ').toLowerCase()}</td>
                  <td className="px-4 py-2 text-slate-600">
                    {e.startDate ? new Date(e.startDate).toLocaleDateString() : '—'}
                    {e.endDate && e.endDate !== e.startDate && ` → ${new Date(e.endDate).toLocaleDateString()}`}
                  </td>
                  <td className="px-4 py-2">
                    {e.published
                      ? <span className="px-2 py-0.5 rounded text-xs bg-green-100 text-green-800">Published</span>
                      : <span className="px-2 py-0.5 rounded text-xs bg-amber-100 text-amber-800">Draft</span>}
                  </td>
                  <td className="px-4 py-2 text-right">
                    <Link
                      href={`/tenants/${tenantId}/academics/exams/${e.id}/marks`}
                      className="text-primary hover:underline mr-3 text-sm"
                    >
                      Enter marks
                    </Link>
                    <RequireRole roles={OWNER_OR_ADMIN}>
                      {!e.published && (
                        <Button variant="ghost" size="sm" onClick={() => {
                          if (confirm(`Publish "${e.name}"? Marks become read-only and report cards become visible to parents.`)) {
                            publish.mutate(e.id);
                          }
                        }} disabled={publish.isPending}>
                          Publish
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

      <CreateExamModal
        open={open}
        tenantId={tenantId}
        onClose={() => setOpen(false)}
        onSuccess={() => { setOpen(false); qc.invalidateQueries({ queryKey: ['exams', tenantId] }); }}
      />
    </div>
  );
}

function CreateExamModal({ open, tenantId, onClose, onSuccess }: {
  open: boolean; tenantId: string; onClose: () => void; onSuccess: () => void;
}) {
  const [form, setForm] = useState<CreateExamRequest>({
    name: '', examType: 'TERM', startDate: '', endDate: '',
  });

  const create = useMutation({
    mutationFn: () => academicsApi.createExam(tenantId, {
      ...form,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
    }),
    onSuccess,
  });

  return (
    <Modal open={open} onClose={onClose} title="Create exam">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        {create.isError && <ErrorBanner error={create.error} />}
        <Input label="Exam name" value={form.name} required maxLength={100}
          placeholder="Half-Yearly 2026"
          onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Type</span>
          <select
            value={form.examType}
            onChange={(e) => setForm({ ...form, examType: e.target.value as ExamType })}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            {EXAM_TYPES.map((t) => <option key={t} value={t}>{t.replace('_', ' ')}</option>)}
          </select>
        </label>
        <div className="grid grid-cols-2 gap-3">
          <Input label="Start date" type="date" value={form.startDate ?? ''}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
          <Input label="End date" type="date" value={form.endDate ?? ''}
            onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || !form.name}>
            {create.isPending ? 'Creating…' : 'Create exam'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
