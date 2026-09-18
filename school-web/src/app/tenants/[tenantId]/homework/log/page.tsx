'use client';

/**
 * Homework Log (admin) — audit of which teacher assigned which homework, to which class, on which
 * day. Sourced from the permanent homework history (created_by + created_at on every assignment).
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { ClipboardList } from 'lucide-react';
import { homeworkApi, type HomeworkLogRow } from '@/api/endpoints/homework';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

const iso = (d: Date) => d.toISOString().slice(0, 10);

export default function HomeworkLogPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <Inner />
    </RequireRole>
  );
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [from, setFrom] = useState(iso(new Date(Date.now() - 30 * 864e5)));
  const [to, setTo] = useState(iso(new Date()));

  const q = useQuery({
    queryKey: ['homework-log', tenantId, from, to],
    queryFn: () => homeworkApi.log(tenantId, from, to),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-5">
      <PageHeader title="Homework Log" description="Which teacher assigned which homework, to which class, on which day." icon={<ClipboardList />} />

      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">From</span>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm" /></label>
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">To</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm" /></label>
      </div>

      {q.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <Card>
          <CardBody className="p-0 overflow-x-auto">
            {q.data.length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-400">No homework assigned in this range.</p>
            ) : (
              <table className="w-full text-sm min-w-[760px]">
                <thead className="border-b border-slate-100">
                  <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                    <th className="px-4 py-3 font-medium">Assigned On</th>
                    <th className="px-4 py-3 font-medium">Teacher</th>
                    <th className="px-4 py-3 font-medium">Class</th>
                    <th className="px-4 py-3 font-medium">Subject</th>
                    <th className="px-4 py-3 font-medium">Homework</th>
                    <th className="px-4 py-3 font-medium">Due</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {q.data.map((r: HomeworkLogRow, i) => (
                    <tr key={i} className="hover:bg-slate-50/60">
                      <td className="px-4 py-3 whitespace-nowrap">{r.assignedOn}</td>
                      <td className="px-4 py-3"><Badge tone="primary">{r.teacherName}</Badge></td>
                      <td className="px-4 py-3">{r.sectionLabel}</td>
                      <td className="px-4 py-3">{r.subjectName}</td>
                      <td className="px-4 py-3">{r.title}</td>
                      <td className="px-4 py-3 text-slate-500">{r.dueDate ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
