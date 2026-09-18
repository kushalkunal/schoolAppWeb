'use client';

import { useQuery } from '@tanstack/react-query';
import { platformApi } from '@/api/endpoints/platform';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';

export default function FeaturesPage() {
  const q = useQuery({
    queryKey: ['platform', 'features'],
    queryFn: () => platformApi.listFeatures(),
  });

  // group by category
  const grouped: Record<string, typeof q.data> = {};
  if (q.data) {
    for (const f of q.data) {
      const cat = f.category ?? 'general';
      (grouped[cat] = grouped[cat] ?? []).push(f);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Feature catalogue</h1>
        <p className="text-sm text-slate-500">
          Master list of feature keys consumed by <code className="bg-slate-200 px-1 rounded text-xs">@RequiresFeature</code>.
          Per-tenant overrides live on each tenant&apos;s detail page.
        </p>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && Object.entries(grouped).map(([cat, items]) => (
        <div key={cat} className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <div className="bg-slate-50 px-4 py-2 text-xs uppercase text-slate-600 font-medium">{cat}</div>
          <table className="w-full text-sm">
            <tbody>
              {(items ?? []).map((f) => (
                <tr key={f.featureKey} className="border-t border-slate-100">
                  <td className="px-4 py-2 w-1/3">
                    <div className="font-medium text-slate-800">{f.name}</div>
                    <code className="text-xs text-slate-400">{f.featureKey}</code>
                  </td>
                  <td className="px-4 py-2 text-slate-600">{f.description ?? '—'}</td>
                  <td className="px-4 py-2 text-right">
                    <span className={`px-2 py-0.5 rounded text-xs ${f.defaultEnabled ? 'bg-green-100 text-green-800' : 'bg-slate-200 text-slate-700'}`}>
                      default {f.defaultEnabled ? 'on' : 'off'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  );
}
