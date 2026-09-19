'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { feesApi } from '@/api/endpoints/fees';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Button } from '@/components/ui/Button';
import { formatINR, formatDate } from '@/lib/utils';

const PAGE_SIZE = 25;

export default function DefaultersPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [page, setPage] = useState(0);

  const q = useQuery({
    queryKey: ['defaulters', tenantId, { page, size: PAGE_SIZE }],
    queryFn: () => feesApi.defaulters(tenantId, { page, size: PAGE_SIZE }),
    enabled: !!tenantId,
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-4">
      <div>
        <Link href={`/tenants/${tenantId}/fees/dashboard`} className="text-sm text-slate-500 hover:underline">
          ← Fees dashboard
        </Link>
        <h1 className="text-2xl font-semibold mt-1">Defaulters</h1>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && q.data.items.length === 0 && (
        <div className="bg-white border border-slate-200 rounded-lg p-10 text-center text-slate-500">
          No outstanding fees. 🎉
        </div>
      )}

      {q.data && q.data.items.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Student</th>
                <th className="text-left px-4 py-2 font-medium">Class</th>
                <th className="text-right px-4 py-2 font-medium">Outstanding</th>
                <th className="text-left px-4 py-2 font-medium">Oldest due</th>
                <th className="text-right px-4 py-2 font-medium">Days overdue</th>
              </tr>
            </thead>
            <tbody>
              {q.data.items.map((d) => (
                <tr key={d.studentId} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2">
                    <Link href={`/tenants/${tenantId}/students/${d.studentId}`} className="text-primary hover:underline">
                      {d.studentName}
                    </Link>
                  </td>
                  <td className="px-4 py-2 text-slate-600">{d.className} · {d.sectionName}</td>
                  <td className="px-4 py-2 text-right font-medium">{formatINR(d.outstandingPaise)}</td>
                  <td className="px-4 py-2 text-slate-600">{formatDate(d.oldestDueDate)}</td>
                  <td className="px-4 py-2 text-right">
                    <span className={d.daysOverdue > 30 ? 'text-danger font-medium' : ''}>
                      {d.daysOverdue}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-4 py-2 border-t border-slate-200 text-xs text-slate-500 flex items-center justify-between bg-slate-50">
            <div>
              {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, q.data.meta.total ?? q.data.items.length)} of {q.data.meta.total ?? q.data.items.length}
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
                Prev
              </Button>
              <Button size="sm" variant="secondary"
                onClick={() => setPage((p) => p + 1)}
                disabled={(page + 1) * PAGE_SIZE >= (q.data.meta.total ?? q.data.items.length)}>
                Next
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
