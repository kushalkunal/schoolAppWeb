'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { platformApi } from '@/api/endpoints/platform';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Button } from '@/components/ui/Button';

export default function PlatformTenantsPage() {
  const [page, setPage] = useState(0);
  const size = 25;

  const q = useQuery({
    queryKey: ['platform', 'tenants', page, size],
    queryFn: () => platformApi.listTenants({ page, size }),
  });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">All tenants</h1>
        <p className="text-sm text-slate-500">Every school on the platform. Click into one to manage its plan, features and provider configs.</p>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && (
        <>
          <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
                <tr>
                  <th className="text-left px-4 py-2 font-medium">School</th>
                  <th className="text-left px-4 py-2 font-medium">State</th>
                  <th className="text-left px-4 py-2 font-medium">Plan</th>
                  <th className="text-left px-4 py-2 font-medium">Status</th>
                  <th className="text-left px-4 py-2 font-medium">Trial ends</th>
                  <th className="text-left px-4 py-2 font-medium">Created</th>
                </tr>
              </thead>
              <tbody>
                {q.data.items.map((t) => (
                  <tr key={t.schoolId} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2">
                      <Link href={`/platform/tenants/${t.schoolId}`} className="text-primary hover:underline font-medium">
                        {t.name}
                      </Link>
                      {!t.active && <span className="ml-2 text-xs text-red-600">inactive</span>}
                    </td>
                    <td className="px-4 py-2 text-slate-600">{t.state ?? '—'}</td>
                    <td className="px-4 py-2 text-slate-600">{t.planCode ?? '—'}</td>
                    <td className="px-4 py-2">
                      <StatusPill status={t.status} />
                    </td>
                    <td className="px-4 py-2 text-slate-600">
                      {t.trialEndsAt ? new Date(t.trialEndsAt).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-4 py-2 text-slate-600">
                      {new Date(t.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
                {q.data.items.length === 0 && (
                  <tr><td colSpan={6} className="text-center py-8 text-slate-500">No tenants</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-500">Page {page + 1}</span>
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                Previous
              </Button>
              <Button variant="secondary" size="sm" disabled={q.data.items.length < size} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function StatusPill({ status }: { status: string | null }) {
  if (!status) return <span className="text-slate-400">—</span>;
  const map: Record<string, string> = {
    TRIAL: 'bg-blue-100 text-blue-800',
    ACTIVE: 'bg-green-100 text-green-800',
    PAST_DUE: 'bg-amber-100 text-amber-800',
    SUSPENDED: 'bg-red-100 text-red-800',
    CANCELLED: 'bg-slate-200 text-slate-700',
  };
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${map[status] ?? 'bg-slate-100 text-slate-700'}`}>
      {status}
    </span>
  );
}
