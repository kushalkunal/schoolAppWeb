'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  FileText, DollarSign, Sparkles, ExternalLink, Calendar, ChevronDown,
  TrendingUp, Wallet,
} from 'lucide-react';
import { hrApi } from '@/api/endpoints/hr';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';
import type { PayslipResponse } from '@/types/domain';

const MONTHS = [
  'January','February','March','April','May','June',
  'July','August','September','October','November','December',
];

const now = new Date();

export default function PayrollPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [staffId, setStaffId]   = useState<string>('');
  const [year, setYear]         = useState<number>(now.getFullYear());
  const [month, setMonth]       = useState<number>(now.getMonth() + 1);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
  });

  const payslipsQ = useQuery({
    queryKey: ['payslips', tenantId, staffId],
    queryFn: () => hrApi.listPayslips(tenantId, staffId),
    enabled: !!tenantId && !!staffId,
    retry: false,
  });

  const generate = useMutation({
    mutationFn: () => hrApi.generatePayslip(tenantId, staffId, year, month),
    onSuccess: (p) => {
      toast.success(`Payslip generated · ₹${(p.netPaise / 100).toLocaleString('en-IN')} net`);
      qc.invalidateQueries({ queryKey: ['payslips', tenantId, staffId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not generate'),
  });

  // Last-12-months options
  const yearOptions = useMemo(() => {
    const y = now.getFullYear();
    return [y, y - 1, y - 2];
  }, []);

  const activeStaff = (staffQ.data ?? []).filter((s) => s.active && s.id);
  const selectedStaff = activeStaff.find((s) => s.id === staffId);

  // Feature-disabled state
  if (payslipsQ.isError && hasCode(payslipsQ.error, 'FEATURE_DISABLED')) {
    return (
      <EmptyState
        icon={<DollarSign size={28} />}
        title="Payroll isn't enabled for your plan"
        description="Salary structures, payslip generation, and PF/ESI computation are part of the Enterprise plan."
      />
    );
  }

  return (
    <div className="space-y-5">
      {/* Selector card */}
      <Card padding="md">
        <div className="grid grid-cols-1 md:grid-cols-[2fr_1fr_1fr_auto] gap-3 items-end">
          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Staff</span>
            <select
              value={staffId}
              onChange={(e) => setStaffId(e.target.value)}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            >
              <option value="">Select staff member</option>
              {activeStaff.map((s) => (
                <option key={s.id} value={s.id!}>{s.displayName} — {s.role.replace(/_/g, ' ').toLowerCase()}</option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Year</span>
            <select
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            >
              {yearOptions.map((y) => <option key={y} value={y}>{y}</option>)}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Month</span>
            <select
              value={month}
              onChange={(e) => setMonth(Number(e.target.value))}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            >
              {MONTHS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
            </select>
          </label>

          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button
              onClick={() => generate.mutate()}
              disabled={!staffId}
              loading={generate.isPending}
              glow
            >
              <Sparkles size={14} /> Generate payslip
            </Button>
          </RequireRole>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Pulls working days from staff attendance + approved leave, applies the active salary
          structure, computes PF / ESI / professional tax → net.
        </p>
      </Card>

      {/* No staff selected — friendly hint */}
      {!staffId && (
        <EmptyState
          icon={<FileText size={28} />}
          title="Pick a staff member to view payslips"
          description="History is per-staff. Once you generate a payslip it shows up in the list below — versioned, so re-issues never overwrite originals."
        />
      )}

      {/* Payslip list */}
      {staffId && (
        <Card padding="none" className="overflow-hidden">
          <CardHeader className="flex items-center justify-between">
            <div>
              <CardTitle>{selectedStaff?.displayName ?? 'Staff'} — payslip history</CardTitle>
              <CardDescription>
                Each row is an immutable monthly snapshot. Re-generation creates a v2 alongside v1.
              </CardDescription>
            </div>
          </CardHeader>

          {payslipsQ.isLoading && <CardBody><Skeleton className="h-32" /></CardBody>}
          {payslipsQ.isError && !hasCode(payslipsQ.error, 'FEATURE_DISABLED') && (
            <CardBody><ErrorBanner error={payslipsQ.error} onRetry={() => payslipsQ.refetch()} /></CardBody>
          )}

          {payslipsQ.data && payslipsQ.data.length === 0 && (
            <CardBody>
              <p className="text-sm text-slate-500 py-8 text-center">
                No payslips yet for this staff member. Generate one above.
              </p>
            </CardBody>
          )}

          {payslipsQ.data && payslipsQ.data.length > 0 && (
            <ul className="divide-y divide-slate-100">
              {payslipsQ.data.map((p) => (
                <PayslipRow
                  key={p.id}
                  payslip={p}
                  expanded={expandedId === p.id}
                  onToggle={() => setExpandedId(expandedId === p.id ? null : p.id)}
                />
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  );
}

// ============================================================
// Row
// ============================================================

function PayslipRow({
  payslip, expanded, onToggle,
}: {
  payslip: PayslipResponse;
  expanded: boolean;
  onToggle: () => void;
}) {
  const month = MONTHS[payslip.month - 1] ?? '';
  return (
    <li>
      <button
        onClick={onToggle}
        className="w-full text-left px-5 py-3 flex items-center justify-between gap-3 hover:bg-slate-50/60 transition"
      >
        <div className="flex items-center gap-3 min-w-0">
          <span className="w-10 h-10 rounded-brand bg-accent-soft text-accent grid place-items-center">
            <Wallet size={16} />
          </span>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm font-semibold text-slate-900">
                {month} {payslip.year}
              </span>
              {payslip.version > 1 && <Badge tone="warning" size="sm">v{payslip.version}</Badge>}
            </div>
            <div className="text-[11px] text-slate-500 flex items-center gap-1">
              <Calendar size={10} /> Generated {new Date(payslip.generatedAt).toLocaleDateString('en-IN')}
              {payslip.leaveDaysUnpaid > 0 && ` · ${payslip.leaveDaysUnpaid} unpaid day${payslip.leaveDaysUnpaid !== 1 ? 's' : ''}`}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="text-right hidden sm:block">
            <div className="text-[10px] text-slate-500 uppercase tracking-wide">Net</div>
            <div className="text-lg font-bold tabular-nums text-slate-900">
              ₹{(payslip.netPaise / 100).toLocaleString('en-IN')}
            </div>
          </div>
          <ChevronDown size={16} className={cn('text-slate-400 transition', expanded && 'rotate-180')} />
        </div>
      </button>

      {expanded && <BreakdownPanel payslip={payslip} />}
    </li>
  );
}

function BreakdownPanel({ payslip }: { payslip: PayslipResponse }) {
  const b = payslip.breakdown as Record<string, number | string | undefined>;
  const fmt = (n?: number | string) => {
    const v = Number(n ?? 0);
    return isFinite(v) ? `₹${(v / 100).toLocaleString('en-IN')}` : '—';
  };

  return (
    <div className="px-5 pb-5 bg-slate-50/40 border-t border-slate-100">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-1.5 text-sm mt-3">
        <div className="font-medium text-slate-700 mb-1">Earnings</div>
        <div className="font-medium text-slate-700 mb-1">Deductions</div>

        <Row label="Basic"             value={fmt(b.basic as number)} />
        <Row label="PF"                value={fmt(b.pf as number)} />

        <Row label="HRA"               value={fmt(b.hra as number)} />
        <Row label="ESI"               value={fmt(b.esi as number)} />

        <Row label="DA"                value={fmt(b.da as number)} />
        <Row label="Professional tax"  value={fmt(b.profTax as number)} />

        <Row label="Special allowance" value={fmt(b.specialAllowance as number)} />
        <Row label="Unpaid leave cut"  value={fmt(b.lwpCut as number)} />

        <Row label="Other allowance"   value={fmt(b.otherAllowance as number)} />
        <Row label=""                  value="" />

        <Row label="Gross"             value={fmt(b.gross as number)}    bold />
        <Row label="Total deductions"  value={fmt(payslip.deductionsPaise)} bold />
      </div>

      <div className="mt-4 flex items-center justify-between gap-3 p-3 rounded-brand bg-brand-gradient text-primary-fg">
        <div className="flex items-center gap-2">
          <TrendingUp size={16} />
          <span className="text-sm font-medium">Net payable</span>
        </div>
        <div className="text-xl font-bold tabular-nums">
          ₹{(payslip.netPaise / 100).toLocaleString('en-IN')}
        </div>
      </div>

      <div className="mt-3 text-[11px] text-slate-500 flex items-center justify-between">
        <span>Working days: {payslip.workingDays}</span>
        {payslip.pdfUrl ? (
          <a href={payslip.pdfUrl} target="_blank" rel="noopener noreferrer"
             className="inline-flex items-center gap-1 text-primary font-medium hover:underline">
            <FileText size={11} /> Open PDF <ExternalLink size={10} />
          </a>
        ) : (
          <span>PDF generation pending</span>
        )}
      </div>
    </div>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between py-1 border-b border-slate-200/40 last:border-0', bold && 'font-semibold text-slate-800 border-t pt-2 mt-1')}>
      <span className="text-slate-500">{label}</span>
      <span className="tabular-nums">{value}</span>
    </div>
  );
}
