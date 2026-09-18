'use client';

/**
 * Substitution & coverage history (admin). Permanent log of who covered which class, and who
 * actually marked that class's attendance — the audit trail for substitute teaching.
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { History } from 'lucide-react';
import { substitutionApi, type HistoryRow } from '@/api/endpoints/substitution';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';

const iso = (d: Date) => d.toISOString().slice(0, 10);

export default function SubstitutionHistoryPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [from, setFrom] = useState(iso(new Date(Date.now() - 30 * 864e5)));
  const [to, setTo] = useState(iso(new Date()));

  const q = useQuery({
    queryKey: ['sub-history', tenantId, from, to],
    queryFn: () => substitutionApi.history(tenantId, from, to),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-5">
      <PageHeader title="Substitution History" description="Who covered which class, and who marked its attendance — a permanent audit trail." icon={<History />} />

      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">From</span>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm" />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">To</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm" />
        </label>
      </div>

      {q.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <Card>
          <CardBody className="p-0 overflow-x-auto">
            {q.data.length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-400">No substitutions in this range.</p>
            ) : (
              <table className="w-full text-sm min-w-[820px]">
                <thead className="border-b border-slate-100">
                  <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium">Class</th>
                    <th className="px-4 py-3 font-medium">Subject</th>
                    <th className="px-4 py-3 font-medium">Period</th>
                    <th className="px-4 py-3 font-medium">Absent Teacher</th>
                    <th className="px-4 py-3 font-medium">Substitute</th>
                    <th className="px-4 py-3 font-medium">Attendance Marked By</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {q.data.map((r: HistoryRow, i) => (
                    <tr key={i} className="hover:bg-slate-50/60">
                      <td className="px-4 py-3 whitespace-nowrap">{r.date}</td>
                      <td className="px-4 py-3">{r.sectionLabel}</td>
                      <td className="px-4 py-3">{r.subjectName}</td>
                      <td className="px-4 py-3">{r.periodName}</td>
                      <td className="px-4 py-3 text-slate-500">{r.absentTeacherName}</td>
                      <td className="px-4 py-3"><Badge tone="primary">{r.substituteName}</Badge></td>
                      <td className="px-4 py-3">
                        {r.attendanceMarkedBy === 'Not marked'
                          ? <Badge tone="warning">Not marked</Badge>
                          : <span>{r.attendanceMarkedBy}</span>}
                      </td>
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
