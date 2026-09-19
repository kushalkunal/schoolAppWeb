'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardCheck, Plus, Settings2, BarChart2, FileText, Trash2 } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { ResultStatus } from '@/types/domain';

export default function ExamsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const q = useQuery({
    queryKey: ['exams', tenantId],
    queryFn: () => academicsApi.listExams(tenantId),
    enabled: !!tenantId,
  });

  const del = useMutation({
    mutationFn: (examId: string) => academicsApi.deleteExam(tenantId, examId),
    onSuccess: () => { toast.success('Exam deleted'); qc.invalidateQueries({ queryKey: ['exams', tenantId] }); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to delete exam'),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Exams</h1>
          <p className="text-sm text-slate-500">
            Create an exam, pick classes, set subjects &amp; schedule, then generate admit cards — all in the guided setup.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Link href={`/tenants/${tenantId}/academics/exams/setup`}>
            <Button><Plus size={16} className="mr-1" /> New exam</Button>
          </Link>
        </RequireRole>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && q.data.length === 0 && (
        <Card>
          <div className="text-center py-8 text-slate-500">
            <ClipboardCheck className="mx-auto mb-2" size={32} />
            <p>No exams yet.</p>
            <RequireRole roles={OWNER_OR_ADMIN}>
              <Link href={`/tenants/${tenantId}/academics/exams/setup`} className="text-primary hover:underline text-sm">
                Start the guided setup →
              </Link>
            </RequireRole>
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
                <th className="text-left px-4 py-2 font-medium">Result</th>
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
                  <td className="px-4 py-2"><ResultStatusBadge status={e.resultStatus ?? 'DRAFT'} /></td>
                  <td className="px-4 py-2 text-right space-x-2 whitespace-nowrap">
                    <Link href={`/tenants/${tenantId}/academics/exams/${e.id}/marks`} className="text-indigo-600 hover:underline text-sm">Marks</Link>
                    <RequireRole roles={OWNER_OR_ADMIN}>
                      <Link href={`/tenants/${tenantId}/academics/exams/${e.id}/structure`} className="text-slate-600 hover:underline text-sm inline-flex items-center gap-0.5"><Settings2 size={13} />Structure</Link>
                      <Link href={`/tenants/${tenantId}/academics/exams/${e.id}/results`} className="text-slate-600 hover:underline text-sm inline-flex items-center gap-0.5"><BarChart2 size={13} />Results</Link>
                      <Link href={`/tenants/${tenantId}/academics/exams/${e.id}/admit-cards`} className="text-slate-600 hover:underline text-sm inline-flex items-center gap-0.5"><FileText size={13} />Admit Cards</Link>
                      <button
                        onClick={() => { if (confirm(`Delete "${e.name}"? This removes its marks, results, schedule and admit cards. This cannot be undone.`)) del.mutate(e.id); }}
                        disabled={del.isPending}
                        className="text-red-600 hover:underline text-sm inline-flex items-center gap-0.5"
                      ><Trash2 size={13} />Delete</button>
                    </RequireRole>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ResultStatusBadge({ status }: { status: ResultStatus | string }) {
  const map: Record<string, { label: string; cls: string }> = {
    DRAFT:     { label: 'Draft',     cls: 'bg-slate-100 text-slate-500' },
    READY:     { label: 'Ready',     cls: 'bg-blue-50 text-blue-600' },
    VERIFIED:  { label: 'Verified',  cls: 'bg-indigo-50 text-indigo-600' },
    PUBLISHED: { label: 'Published', cls: 'bg-green-50 text-green-700' },
  };
  const s = map[status] ?? { label: String(status), cls: 'bg-slate-100 text-slate-500' };
  return <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${s.cls}`}>{s.label}</span>;
}
