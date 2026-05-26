'use client';

import { useQuery } from '@tanstack/react-query';
import { platformApi } from '@/api/endpoints/platform';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';

export default function PlansPage() {
  const q = useQuery({
    queryKey: ['platform', 'plans'],
    queryFn: () => platformApi.listPlans(),
  });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Plans</h1>
        <p className="text-sm text-slate-500">
          Plan catalogue. Read-only here — plans are seeded by Flyway and edited via DB migration so
          billing audit history stays clean.
        </p>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Code</th>
                <th className="text-left px-4 py-2 font-medium">Name</th>
                <th className="text-left px-4 py-2 font-medium">Description</th>
                <th className="text-right px-4 py-2 font-medium">Price (₹/mo)</th>
                <th className="text-left px-4 py-2 font-medium">Active</th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((p) => (
                <tr key={p.id} className="border-t border-slate-100">
                  <td className="px-4 py-2 font-mono text-xs text-slate-700">{p.code}</td>
                  <td className="px-4 py-2 font-medium">{p.name}</td>
                  <td className="px-4 py-2 text-slate-600">{p.description ?? '—'}</td>
                  <td className="px-4 py-2 text-right">{(p.monthlyPricePaise / 100).toLocaleString('en-IN')}</td>
                  <td className="px-4 py-2">{p.active ? 'Yes' : 'No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
