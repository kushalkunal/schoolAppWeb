'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { feesApi } from '@/api/endpoints/fees';
import { Card, CardBody } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Button } from '@/components/ui/Button';
import { formatINR } from '@/lib/utils';
import { FEE_WRITER, RequireRole } from '@/auth/RequireRole';

export default function FeesDashboardPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const q = useQuery({
    queryKey: ['fee-dashboard', tenantId],
    queryFn: () => feesApi.dashboard(tenantId),
    enabled: !!tenantId,
  });

  if (q.isLoading) return <div className="text-slate-500 flex items-center gap-2"><Spinner /> Loading…</div>;
  if (q.isError) return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data!;

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Fees</h1>
          <p className="text-sm text-slate-500">Collection + outstanding at a glance.</p>
        </div>
        <RequireRole roles={FEE_WRITER}>
          <Link href={`/tenants/${tenantId}/fees/collect`}>
            <Button>Quick collect</Button>
          </Link>
        </RequireRole>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <Tile label="Collected today" value={formatINR(d.collectedTodayPaise)} sub={`${d.paymentsCollectedToday} payments`} />
        <Tile label="Collected this month" value={formatINR(d.collectedThisMonthPaise)} />
        <Tile label="Outstanding" value={formatINR(d.totalOutstandingPaise)} sub={`${d.studentsWithDues} students`} accent="warn" />
        <Tile label="Overdue" value={formatINR(d.totalOverduePaise)} accent={d.totalOverduePaise > 0 ? 'danger' : undefined} />
      </div>

      <Card>
        <CardBody>
          <div className="flex flex-col gap-2 text-sm">
            <Link href={`/tenants/${tenantId}/fees/defaulters`} className="text-primary hover:underline">
              View defaulters list →
            </Link>
            <Link href={`/tenants/${tenantId}/fees/structure`} className="text-primary hover:underline">
              Manage fee structure (per-class amounts + bulk invoice generation) →
            </Link>
            <Link href={`/tenants/${tenantId}/fees/cash-recon`} className="text-primary hover:underline">
              Day-end cash reconciliation →
            </Link>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}

function Tile({ label, value, sub, accent }: {
  label: string; value: string; sub?: string; accent?: 'warn' | 'danger';
}) {
  const colour = accent === 'danger' ? 'text-danger' : accent === 'warn' ? 'text-warning' : 'text-slate-900';
  const border = accent === 'danger' ? 'border-red-300' : accent === 'warn' ? 'border-amber-300' : 'border-slate-200';
  return (
    <div className={`bg-white rounded-lg border p-4 ${border}`}>
      <div className="text-xs uppercase text-slate-500 tracking-wide">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${colour}`}>{value}</div>
      {sub && <div className="text-xs text-slate-500 mt-1">{sub}</div>}
    </div>
  );
}
