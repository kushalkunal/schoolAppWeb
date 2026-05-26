'use client';

/**
 * Day-end cash reconciliation. Accountant picks a date → sees expected totals (from
 * fee_payments aggregated by mode) → punches in counted amounts → variance is computed.
 * Gated by CASH_RECONCILIATION + role FEE_WRITER.
 */
import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cashReconApi, type CloseDrawerRequest } from '@/api/endpoints/dailyOps';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { FEE_WRITER, RequireRole } from '@/auth/RequireRole';
import { formatINR, cn } from '@/lib/utils';
import { isFeatureEnabled } from '@/features/featureFlags';
import { EmptyState } from '@/components/ui/EmptyState';

export default function CashReconPage() {
  if (!isFeatureEnabled('CASH_RECONCILIATION')) {
    return <EmptyState title="Cash reconciliation is disabled" />;
  }
  return <RequireRole roles={FEE_WRITER}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const { success, error: toastErr } = useToast();
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [counted, setCounted] = useState<CloseDrawerRequest>({
    date, countedCashPaise: 0, countedUpiPaise: 0, countedChequePaise: 0, countedOtherPaise: 0,
  });

  const expectedQ = useQuery({
    queryKey: ['cash-recon-expected', tenantId, date],
    queryFn: () => cashReconApi.expected(tenantId, date),
    enabled: !!tenantId,
  });
  const historyQ = useQuery({
    queryKey: ['cash-recon-history', tenantId],
    queryFn: () => cashReconApi.history(tenantId),
    enabled: !!tenantId,
  });

  const closeMutation = useMutation({
    mutationFn: () => cashReconApi.close(tenantId, { ...counted, date }),
    onSuccess: (r) => {
      success(`Drawer closed · variance ${formatINR(r.variancePaise)}`);
      qc.invalidateQueries({ queryKey: ['cash-recon-history', tenantId] });
    },
    onError: (e: Error) => toastErr(e.message),
  });

  const totalCounted = counted.countedCashPaise + counted.countedUpiPaise
    + counted.countedChequePaise + counted.countedOtherPaise;
  const variance = totalCounted - (expectedQ.data?.totalPaise ?? 0);

  return (
    <div className="space-y-4">
      <PageHeader title="Day-end cash reconciliation"
        description="Compare counted cash + receipts against the system-recorded payments." />

      <Card>
        <CardBody className="space-y-3">
          <div className="flex items-center gap-3">
            <label className="text-sm">Closing date:</label>
            <input type="date" className="border border-slate-200 rounded px-2 py-1 text-sm"
              value={date} onChange={(e) => setDate(e.target.value)} />
          </div>

          {expectedQ.isLoading ? <Spinner /> : expectedQ.isError ? <ErrorBanner error={expectedQ.error} /> : (
            <table className="w-full text-sm">
              <thead className="text-xs text-slate-500 text-left">
                <tr><th>Mode</th><th className="text-right">Expected</th><th className="text-right">Counted</th></tr>
              </thead>
              <tbody>
                {(['Cash', 'UPI / Online', 'Cheque / DD', 'Other'] as const).map((label, i) => {
                  const expected = [expectedQ.data?.expectedCashPaise, expectedQ.data?.expectedUpiPaise, expectedQ.data?.expectedChequePaise, expectedQ.data?.expectedOtherPaise][i] ?? 0;
                  const keys: (keyof CloseDrawerRequest)[] = ['countedCashPaise', 'countedUpiPaise', 'countedChequePaise', 'countedOtherPaise'];
                  const k = keys[i]!;
                  return (
                    <tr key={label} className="border-t border-slate-100">
                      <td className="py-2">{label}</td>
                      <td className="text-right font-mono">{formatINR(expected)}</td>
                      <td className="text-right">
                        <input type="number" min={0} className="w-32 text-right border border-slate-200 rounded px-2 py-1"
                          value={(counted[k] as number) / 100 || ''}
                          onChange={(e) => setCounted({ ...counted, [k]: Math.round(Number(e.target.value) * 100) })} />
                      </td>
                    </tr>
                  );
                })}
                <tr className="border-t border-slate-200 font-semibold">
                  <td className="py-2">Total</td>
                  <td className="text-right font-mono">{formatINR(expectedQ.data?.totalPaise ?? 0)}</td>
                  <td className="text-right font-mono">{formatINR(totalCounted)}</td>
                </tr>
                <tr>
                  <td className="py-2">Variance</td>
                  <td colSpan={2} className={cn(
                    'text-right font-mono font-semibold',
                    variance === 0 ? 'text-emerald-600' : 'text-rose-600',
                  )}>{formatINR(variance)}</td>
                </tr>
              </tbody>
            </table>
          )}

          <textarea
            placeholder="Notes (optional)"
            className="w-full border border-slate-200 rounded p-2 text-sm"
            rows={2}
            value={counted.notes ?? ''}
            onChange={(e) => setCounted({ ...counted, notes: e.target.value })}
          />
          <div className="flex justify-end">
            <Button onClick={() => closeMutation.mutate()} disabled={closeMutation.isPending}>
              {closeMutation.isPending ? 'Closing…' : 'Close drawer'}
            </Button>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <h2 className="text-sm font-semibold mb-3">History</h2>
          {historyQ.isLoading ? <Spinner /> : (historyQ.data ?? []).length === 0 ? (
            <p className="text-sm text-slate-500">No closures yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-xs text-slate-500 text-left">
                <tr><th>Date</th><th className="text-right">Expected</th><th className="text-right">Counted</th><th className="text-right">Variance</th><th>Notes</th></tr>
              </thead>
              <tbody>
                {(historyQ.data ?? []).map((r) => {
                  const expectedTotal = r.expectedCashPaise + r.expectedUpiPaise + r.expectedChequePaise + r.expectedOtherPaise;
                  const countedTotal = r.countedCashPaise + r.countedUpiPaise + r.countedChequePaise + r.countedOtherPaise;
                  return (
                    <tr key={r.id} className="border-t border-slate-100">
                      <td className="py-1.5">{r.closedOnDate}</td>
                      <td className="text-right font-mono">{formatINR(expectedTotal)}</td>
                      <td className="text-right font-mono">{formatINR(countedTotal)}</td>
                      <td className={cn('text-right font-mono', r.variancePaise === 0 ? 'text-emerald-600' : 'text-rose-600')}>
                        {formatINR(r.variancePaise)}
                      </td>
                      <td className="text-xs text-slate-500 truncate">{r.notes ?? '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
