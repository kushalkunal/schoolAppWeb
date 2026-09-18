'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle, ArrowUpRight, Bot, RefreshCw, TrendingDown,
  TrendingUp, Minus, Wallet, UserX, GraduationCap,
} from 'lucide-react';
import { riskApi } from '@/api/endpoints/risk';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton, SkeletonRows } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';
import type { RiskFactor, StudentRiskScore } from '@/types/domain';

/**
 * Full at-risk student list. Reads `/risk/students?minScore=…` with a tunable
 * threshold and surfaces the LLM narrative the nightly cron wrote.
 *
 * Three threshold presets — High (≥80), Medium (≥60), All (≥0) — control the
 * fetch. Refresh is a manual `POST /risk/recompute` (gated to OWNER_OR_ADMIN).
 */
export default function RiskPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [threshold, setThreshold] = useState<number>(60);
  const [factor, setFactor] = useState<RiskFactor | 'ALL'>('ALL');

  const q = useQuery({
    queryKey: ['risk', tenantId, 'list', threshold],
    queryFn: () => riskApi.list(tenantId, { minScore: threshold, size: 200 }),
    enabled: !!tenantId,
    retry: false,
  });

  const recompute = useMutation({
    mutationFn: () => riskApi.recompute(tenantId),
    onSuccess: (r) => {
      toast.success(`Recomputed ${r.scored} students · ${r.alerts} new alerts`);
      qc.invalidateQueries({ queryKey: ['risk', tenantId] });
      qc.invalidateQueries({ queryKey: ['dashboard', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not recompute'),
  });

  // Feature-flag aware empty
  if (q.isError && hasCode(q.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="At-risk students"
          description="AI-summarised composite risk score across attendance, fees, and marks"
          icon={<AlertTriangle size={18} />}
        />
        <EmptyState
          icon={<Bot size={28} />}
          title="AI risk-scoring isn't enabled for your plan"
          description="Surface struggling students with LLM-written narratives before parents notice. Available on the Enterprise plan."
        />
      </div>
    );
  }

  const rows = q.data ?? [];
  const filtered = factor === 'ALL' ? rows : rows.filter((r) => r.topFactor === factor);

  // Bucket counts for the filter chips
  const byFactor = bucketByFactor(rows);

  return (
    <div className="space-y-6">
      <PageHeader
        title="At-risk students"
        description="Combined risk score across attendance, fees, and marks — refreshed nightly. Click any row for the student's full profile."
        icon={<AlertTriangle size={18} />}
        actions={
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button
              variant="secondary"
              onClick={() => recompute.mutate()}
              loading={recompute.isPending}
            >
              <RefreshCw size={14} className={recompute.isPending ? 'animate-spin' : ''} />
              Recompute now
            </Button>
          </RequireRole>
        }
      />

      {/* Threshold + factor filter bar */}
      <Card className="flex flex-wrap items-center gap-3" padding="md">
        <div className="flex items-center gap-1.5 text-xs font-medium text-slate-600 uppercase tracking-wide">
          Threshold
        </div>
        <div className="flex gap-1 bg-slate-100 rounded-brand p-1">
          {[
            { v: 80, label: 'High (≥80)' },
            { v: 60, label: 'Medium (≥60)' },
            { v: 0,  label: 'All scored' },
          ].map((opt) => (
            <button
              key={opt.v}
              onClick={() => setThreshold(opt.v)}
              className={cn(
                'px-3 py-1 rounded-[calc(var(--brand-radius)-2px)] text-xs font-medium transition',
                threshold === opt.v ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <div className="ml-auto flex items-center gap-1.5">
          <FactorChip label="All" count={rows.length} active={factor === 'ALL'} onClick={() => setFactor('ALL')} />
          <FactorChip label="Attendance" count={byFactor.ATTENDANCE} icon={<UserX size={11} />}
            active={factor === 'ATTENDANCE'} onClick={() => setFactor('ATTENDANCE')} />
          <FactorChip label="Fees" count={byFactor.FEE} icon={<Wallet size={11} />}
            active={factor === 'FEE'} onClick={() => setFactor('FEE')} />
          <FactorChip label="Marks" count={byFactor.MARKS} icon={<GraduationCap size={11} />}
            active={factor === 'MARKS'} onClick={() => setFactor('MARKS')} />
        </div>
      </Card>

      {/* Body */}
      {q.isLoading && (
        <Card padding="md"><SkeletonRows rows={6} /></Card>
      )}

      {q.isError && !hasCode(q.error, 'FEATURE_DISABLED') && (
        <ErrorBanner error={q.error} onRetry={() => q.refetch()} />
      )}

      {!q.isLoading && !q.isError && filtered.length === 0 && (
        <EmptyState
          icon={<AlertTriangle size={28} />}
          title="No students above this threshold"
          description={
            threshold === 0
              ? 'No risk scores yet. Run the nightly recompute or click "Recompute now".'
              : 'Lower the threshold or change the factor filter to see fewer-flagged students.'
          }
        />
      )}

      <div className="space-y-2">
        {filtered.map((r) => <RiskRow key={r.id} tenantId={tenantId} risk={r} />)}
      </div>
    </div>
  );
}

// ------------------------- Row -------------------------

function RiskRow({ tenantId, risk }: { tenantId: string; risk: StudentRiskScore }) {
  const scoreTone = risk.score >= 80 ? 'danger' : risk.score >= 60 ? 'warning' : 'info';
  return (
    <Link
      href={`/tenants/${tenantId}/students/${risk.studentId}`}
      className="block group"
    >
      <Card className="p-4 hover:border-primary hover:shadow-sm transition-all" padding="none">
        <div className="flex items-start gap-4">
          {/* Score donut */}
          <ScoreDonut score={risk.score} />

          {/* Body */}
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-1.5">
              {risk.topFactor && <Badge tone="warning" size="sm">{prettyFactor(risk.topFactor)}</Badge>}
              {risk.marksTrend && risk.marksTrend !== 'UNKNOWN' && <TrendBadge trend={risk.marksTrend} />}
              {risk.attendancePct != null && (
                <span className="text-[11px] text-slate-500">
                  Attendance <span className="font-medium text-slate-700">{Math.round(Number(risk.attendancePct))}%</span>
                </span>
              )}
              {risk.feeOutstandingPaise > 0 && (
                <span className="text-[11px] text-slate-500">
                  Fee due <span className="font-medium text-slate-700">₹{(risk.feeOutstandingPaise / 100).toLocaleString('en-IN')}</span>
                </span>
              )}
            </div>

            {risk.summary && (
              <p className="text-sm text-slate-800 mt-2 leading-snug">{risk.summary}</p>
            )}

            <div className="text-[11px] text-slate-400 mt-1.5">
              Updated {new Date(risk.calculatedAt).toLocaleString('en-IN')}
            </div>
          </div>

          {/* Hover arrow */}
          <ArrowUpRight size={16} className="text-slate-400 group-hover:text-primary transition shrink-0 mt-1" />
        </div>
      </Card>
    </Link>
  );
}

function ScoreDonut({ score }: { score: number }) {
  // Pure-CSS conic gradient for the donut — no chart lib needed.
  const angle = Math.min(score, 100) * 3.6;
  const color = score >= 80 ? 'var(--brand-primary)' : score >= 60 ? '#f59e0b' : '#0ea5e9';
  return (
    <div
      className="w-14 h-14 rounded-full grid place-items-center shrink-0 shadow-sm"
      style={{
        background: `conic-gradient(${color} 0deg ${angle}deg, #e2e8f0 ${angle}deg 360deg)`,
      }}
    >
      <div className="w-10 h-10 rounded-full bg-white grid place-items-center">
        <span className="text-sm font-bold tabular-nums text-slate-800">{score}</span>
      </div>
    </div>
  );
}

function TrendBadge({ trend }: { trend: string }) {
  const Icon = trend === 'DOWN' ? TrendingDown : trend === 'UP' ? TrendingUp : Minus;
  const tone = trend === 'DOWN' ? 'danger' : trend === 'UP' ? 'success' : 'neutral';
  return (
    <Badge tone={tone} size="sm">
      <Icon size={10} /> {trend.toLowerCase()}
    </Badge>
  );
}

function FactorChip({
  label, count, icon, active, onClick,
}: { label: string; count: number; icon?: React.ReactNode; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 h-7 rounded-full text-xs font-medium transition',
        active
          ? 'bg-primary text-primary-fg'
          : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
      )}
    >
      {icon}
      {label}
      <span className={cn('px-1.5 py-0.5 rounded-full text-[10px] tabular-nums',
        active ? 'bg-white/20' : 'bg-white text-slate-500')}>
        {count}
      </span>
    </button>
  );
}

function prettyFactor(f: RiskFactor): string {
  return f.charAt(0) + f.slice(1).toLowerCase();
}

function bucketByFactor(rows: StudentRiskScore[]): Record<RiskFactor, number> {
  const init: Record<RiskFactor, number> = { ATTENDANCE: 0, FEE: 0, MARKS: 0 };
  return rows.reduce<Record<RiskFactor, number>>((acc, r) => {
    if (r.topFactor && acc[r.topFactor] != null) acc[r.topFactor]!++;
    return acc;
  }, init);
}
