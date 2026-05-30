'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import {
  AlertTriangle, ArrowRight, Banknote, BarChart3, Bell, CheckCircle2, ChevronRight, Clock,
  ExternalLink, Settings2, TrendingDown, Users, Wallet, X,
} from 'lucide-react';
import { feesApi } from '@/api/endpoints/fees';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Skeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { formatINR, formatDate, cn } from '@/lib/utils';
import { FEE_WRITER, FEE_CONFIG_EDITOR, RequireRole, useHasRole } from '@/auth/RequireRole';
import type { ClassCollectionRow, DefaulterResponse, RecentPaymentRow } from '@/types/domain';

// ─── Types ───────────────────────────────────────────────────────────────────
type StatusFilter = 'ALL' | 'HAS_DUES' | 'OVERDUE';
type Tab = 'overview' | 'class' | 'defaulters' | 'recent';

export default function FeesDashboardPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [tab, setTab] = useState<Tab>('overview');
  const isConfigEditor = useHasRole('SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN');

  // KPI — always loaded
  const dashQ = useQuery({
    queryKey: ['fee-dashboard', tenantId],
    queryFn: () => feesApi.dashboard(tenantId),
    enabled: !!tenantId,
    staleTime: 60_000,
  });

  // Class collection — loaded when class tab active
  const classQ = useQuery({
    queryKey: ['fee-class-report', tenantId],
    queryFn: () => feesApi.classWiseReport(tenantId),
    enabled: !!tenantId && tab === 'class',
    staleTime: 120_000,
  });

  // All defaulters — used by both class & defaulters tabs
  const defaultersQ = useQuery({
    queryKey: ['fee-defaulters-all', tenantId],
    queryFn: () => feesApi.defaulters(tenantId, { page: 0, size: 500 }),
    enabled: !!tenantId && (tab === 'defaulters' || tab === 'class'),
    staleTime: 120_000,
  });

  // Recent payments
  const recentQ = useQuery({
    queryKey: ['fee-recent', tenantId],
    queryFn: () => feesApi.recentPayments(tenantId, 30),
    enabled: !!tenantId && tab === 'recent',
  });

  if (dashQ.isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1,2,3,4].map((i) => <Skeleton key={i} className="h-24 w-full" />)}
        </div>
      </div>
    );
  }
  if (dashQ.isError) return <ErrorBanner error={dashQ.error} onRetry={() => dashQ.refetch()} />;
  const d = dashQ.data!

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex justify-between items-center flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Fees</h1>
          <p className="text-sm text-slate-500">Collection, outstanding, and reports at a glance.</p>
        </div>
        <div className="flex items-center gap-2">
          {isConfigEditor && (
            <Link href={`/tenants/${tenantId}/fees/structure`}>
              <Button variant="secondary" size="sm">
                <Settings2 size={13} className="mr-1" /> Configure fees
              </Button>
            </Link>
          )}
          <RequireRole roles={FEE_WRITER}>
            <Link href={`/tenants/${tenantId}/fees/collect`}>
              <Button size="sm"><Banknote size={13} className="mr-1" /> Collect fee</Button>
            </Link>
          </RequireRole>
        </div>
      </div>

      {/* KPI tiles */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiTile
          icon={<CheckCircle2 size={18} className="text-green-600" />}
          label="Collected today"
          value={formatINR(d.collectedTodayPaise)}
          sub={`${d.paymentsCollectedToday} payment${d.paymentsCollectedToday === 1 ? '' : 's'}`}
          border="border-green-200 bg-green-50/30"
        />
        <KpiTile
          icon={<Wallet size={18} className="text-blue-600" />}
          label="This month"
          value={formatINR(d.collectedThisMonthPaise)}
          border="border-blue-200 bg-blue-50/30"
        />
        <KpiTile
          icon={<Clock size={18} className="text-amber-600" />}
          label="Outstanding"
          value={formatINR(d.totalOutstandingPaise)}
          sub={`${d.studentsWithDues} students`}
          border="border-amber-300 bg-amber-50/30"
          valueClass="text-amber-700"
        />
        <KpiTile
          icon={<AlertTriangle size={18} className="text-red-600" />}
          label="Overdue"
          value={formatINR(d.totalOverduePaise)}
          border={d.totalOverduePaise > 0 ? 'border-red-300 bg-red-50/30' : 'border-slate-200'}
          valueClass={d.totalOverduePaise > 0 ? 'text-red-700' : undefined}
        />
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200 overflow-x-auto">
        {([
          { key: 'overview',   label: 'Overview',        icon: <BarChart3 size={13} /> },
          { key: 'class',      label: 'Class status',    icon: <Users size={13} /> },
          { key: 'defaulters', label: 'Defaulters',      icon: <AlertTriangle size={13} /> },
          { key: 'recent',     label: 'Recent payments', icon: <Clock size={13} /> },
        ] as const).map(({ key, label, icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={cn(
              'flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition whitespace-nowrap',
              tab === key
                ? 'border-primary text-primary'
                : 'border-transparent text-slate-500 hover:text-slate-800',
            )}
          >
            {icon}{label}
            {key === 'defaulters' && d.studentsWithDues > 0 && (
              <span className="ml-1 bg-red-100 text-red-700 text-[10px] font-semibold px-1.5 py-0.5 rounded-full">
                {d.studentsWithDues}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ─── Overview Tab ─── */}
      {tab === 'overview' && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Card>
            <CardBody className="space-y-1.5 text-sm">
              <p className="font-semibold text-slate-700 mb-3">Quick actions</p>
              <NavLink href={`/tenants/${tenantId}/fees/collect`}    label="Collect fee"                   icon={<Banknote size={14} />} />
              <NavLink href={`/tenants/${tenantId}/fees/defaulters`} label="View defaulters"               icon={<TrendingDown size={14} />} />
              <NavLink href={`/tenants/${tenantId}/fees/reminders`}  label="Reminder schedules"            icon={<Bell size={14} />} />
              <NavLink href={`/tenants/${tenantId}/fees/cash-recon`} label="Day-end cash reconciliation"   icon={<BarChart3 size={14} />} />
            </CardBody>
          </Card>
          <Card>
            <CardBody className="space-y-1.5 text-sm">
              <p className="font-semibold text-slate-700 mb-3">Fee administration</p>
              {isConfigEditor ? (
                <>
                  <NavLink href={`/tenants/${tenantId}/fees/structure`} label="Configure fee structure" icon={<Settings2 size={14} />} />
                  <NavLink href={`/tenants/${tenantId}/fees/heads`}     label="Manage fee heads"        icon={<Wallet size={14} />} />
                </>
              ) : (
                <NavLink href={`/tenants/${tenantId}/fees/structure`} label="View fee structure" icon={<Settings2 size={14} />} />
              )}
              <NavLink
                href={`/tenants/${tenantId}/fees/defaulters`}
                label={`${d.studentsWithDues} students with dues`}
                icon={<AlertTriangle size={14} />}
                accent
              />
            </CardBody>
          </Card>
        </div>
      )}

      {/* ─── Class Status Tab ─── */}
      {tab === 'class' && (
        <ClassStatusTab
          tenantId={tenantId}
          classRows={classQ.data ?? []}
          defaulters={defaultersQ.data?.items ?? []}
          loading={classQ.isLoading || defaultersQ.isLoading}
          error={classQ.error || defaultersQ.error}
          onRetry={() => { classQ.refetch(); defaultersQ.refetch(); }}
        />
      )}

      {/* ─── Defaulters Tab ─── */}
      {tab === 'defaulters' && (
        <DefaultersTab
          tenantId={tenantId}
          defaulters={defaultersQ.data?.items ?? []}
          loading={defaultersQ.isLoading}
          error={defaultersQ.error}
          onRetry={() => defaultersQ.refetch()}
        />
      )}

      {/* ─── Recent Payments Tab ─── */}
      {tab === 'recent' && (
        <Card className="p-0 overflow-hidden">
          <CardHeader>
            <CardTitle>Recent payments</CardTitle>
            <CardDescription>Last 30 payments across all students</CardDescription>
          </CardHeader>
          <CardBody className="p-0">
            {recentQ.isLoading && (
              <div className="p-5 space-y-3">
                {[1,2,3].map((i) => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            )}
            {recentQ.isError && <div className="p-5"><ErrorBanner error={recentQ.error} /></div>}
            {recentQ.data && recentQ.data.length === 0 && (
              <p className="p-5 text-sm text-slate-500">No payments recorded yet.</p>
            )}
            {recentQ.data && recentQ.data.length > 0 && (
              <RecentPaymentsTable payments={recentQ.data} tenantId={tenantId} />
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}

// ───────────────────────────────────────────────────────────────────────────
// Class Status Tab
// ───────────────────────────────────────────────────────────────────────────

function ClassStatusTab({
  tenantId, classRows, defaulters, loading, error, onRetry,
}: {
  tenantId: string;
  classRows: ClassCollectionRow[];
  defaulters: DefaulterResponse[];
  loading: boolean;
  error: unknown;
  onRetry: () => void;
}) {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [selectedClass, setSelectedClass] = useState<string | null>(null);

  // Index defaulters by className for O(1) look-up
  const defaultersByClass = useMemo(() => {
    const map = new Map<string, DefaulterResponse[]>();
    defaulters.forEach((d) => {
      if (!map.has(d.className)) map.set(d.className, []);
      map.get(d.className)!.push(d);
    });
    return map;
  }, [defaulters]);

  if (loading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {[1,2,3,4,5,6].map((i) => <Skeleton key={i} className="h-28 w-full" />)}
      </div>
    );
  }
  if (error) return <ErrorBanner error={error} onRetry={onRetry} />;

  const filteredRows = classRows.filter((r) => {
    if (statusFilter === 'ALL')      return true;
    if (statusFilter === 'HAS_DUES') return r.outstandingPaise > 0;
    if (statusFilter === 'OVERDUE') {
      const cd = defaultersByClass.get(r.className) ?? [];
      return cd.some((d) => d.daysOverdue > 0);
    }
    return true;
  });

  const selectedRow       = selectedClass ? classRows.find((r) => r.className === selectedClass) : null;
  const selectedDefaulters = selectedClass ? (defaultersByClass.get(selectedClass) ?? []) : [];

  return (
    <div className="space-y-4">
      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-slate-500 font-medium">Filter:</span>
        {([
          { key: 'ALL',      label: 'All classes' },
          { key: 'HAS_DUES', label: 'Has outstanding' },
          { key: 'OVERDUE',  label: 'Overdue' },
        ] as const).map(({ key, label }) => (
          <button
            key={key}
            type="button"
            onClick={() => setStatusFilter(key)}
            className={cn(
              'px-3 py-1 text-xs font-medium rounded-full border transition',
              statusFilter === key
                ? 'bg-primary text-white border-primary'
                : 'bg-white text-slate-600 border-slate-300 hover:border-primary hover:text-primary',
            )}
          >
            {label}
          </button>
        ))}
        {classRows.length > 0 && (
          <span className="ml-auto text-xs text-slate-400">
            {filteredRows.length} of {classRows.length} classes
          </span>
        )}
      </div>

      {filteredRows.length === 0 && (
        <div className="bg-white border border-slate-200 rounded-xl p-10 text-center text-slate-500 text-sm">
          No classes match the selected filter.
        </div>
      )}

      {/* Class cards grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredRows.map((row) => {
          const total = row.collectedPaise + row.outstandingPaise;
          const pct   = total > 0 ? Math.round((row.collectedPaise / total) * 100) : 100;
          const cd    = defaultersByClass.get(row.className) ?? [];
          const overdueCount = cd.filter((x) => x.daysOverdue > 0).length;
          const isSelected   = selectedClass === row.className;

          return (
            <button
              key={row.classId}
              type="button"
              onClick={() => setSelectedClass(isSelected ? null : row.className)}
              className={cn(
                'text-left bg-white rounded-xl border p-4 hover:shadow-sm transition-all',
                isSelected
                  ? 'border-primary ring-1 ring-primary shadow-sm'
                  : 'border-slate-200 hover:border-slate-300',
              )}
            >
              <div className="flex items-center justify-between mb-2">
                <span className="font-semibold text-slate-900">{row.className}</span>
                <span className="text-xs text-slate-500">{row.studentCount} students</span>
              </div>
              {/* Progress bar */}
              <div className="h-2 bg-slate-100 rounded-full overflow-hidden mb-2">
                <div
                  className={cn(
                    'h-full rounded-full transition-all',
                    pct >= 80 ? 'bg-green-500' : pct >= 50 ? 'bg-amber-400' : 'bg-red-500',
                  )}
                  style={{ width: `${pct}%` }}
                />
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-green-700 font-medium">{formatINR(row.collectedPaise)} collected</span>
                {row.outstandingPaise > 0 ? (
                  <span className="text-amber-700 font-medium">{formatINR(row.outstandingPaise)} due</span>
                ) : (
                  <span className="text-green-600 font-medium">All paid ✓</span>
                )}
              </div>
              {overdueCount > 0 && (
                <div className="mt-2 text-[11px] text-red-600 font-medium">
                  {overdueCount} overdue student{overdueCount !== 1 ? 's' : ''}
                </div>
              )}
              <div className="mt-1 text-[11px] font-semibold text-right text-slate-400">
                {pct}% collected · click to drill down
              </div>
            </button>
          );
        })}
      </div>

      {/* Drill-down panel */}
      {selectedClass && selectedRow && (
        <ClassDrillDown
          tenantId={tenantId}
          className={selectedClass}
          classRow={selectedRow}
          defaulters={selectedDefaulters}
          onClose={() => setSelectedClass(null)}
        />
      )}
    </div>
  );
}

function ClassDrillDown({
  tenantId, className, classRow, defaulters, onClose,
}: {
  tenantId: string;
  className: string;
  classRow: ClassCollectionRow;
  defaulters: DefaulterResponse[];
  onClose: () => void;
}) {
  const total = classRow.collectedPaise + classRow.outstandingPaise;
  const pct   = total > 0 ? Math.round((classRow.collectedPaise / total) * 100) : 100;
  const overdueStudents = defaulters.filter((d) => d.daysOverdue > 0);

  return (
    <Card className="border-primary/30">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>{className} — Fee status</CardTitle>
            <CardDescription>
              {classRow.studentCount} enrolled · {classRow.paymentCount} payments this period
            </CardDescription>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg"
          >
            <X size={16} />
          </button>
        </div>
      </CardHeader>
      <CardBody>
        {/* Summary chips */}
        <div className="grid grid-cols-3 gap-3 mb-4">
          <div className="bg-green-50 border border-green-200 rounded-lg px-3 py-2 text-center">
            <div className="text-lg font-bold text-green-700">{formatINR(classRow.collectedPaise)}</div>
            <div className="text-[11px] text-green-600 mt-0.5">Collected</div>
          </div>
          <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-center">
            <div className="text-lg font-bold text-amber-700">{formatINR(classRow.outstandingPaise)}</div>
            <div className="text-[11px] text-amber-600 mt-0.5">Outstanding</div>
          </div>
          <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-center">
            <div className="text-lg font-bold text-slate-700">{pct}%</div>
            <div className="text-[11px] text-slate-500 mt-0.5">Collected</div>
          </div>
        </div>

        {defaulters.length === 0 ? (
          <div className="text-center py-6 text-sm text-green-700 bg-green-50 rounded-lg">
            <CheckCircle2 size={24} className="mx-auto mb-1 text-green-500" />
            All students in {className} are up to date!
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between mb-2">
              <p className="text-sm font-semibold text-slate-700">
                Students with outstanding dues ({defaulters.length})
              </p>
              {overdueStudents.length > 0 && (
                <span className="text-xs text-red-600 font-medium bg-red-50 px-2 py-0.5 rounded-full">
                  {overdueStudents.length} overdue
                </span>
              )}
            </div>
            <div className="overflow-x-auto rounded-lg border border-slate-200">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="text-left px-3 py-2 font-medium">Student</th>
                    <th className="text-left px-3 py-2 font-medium">Section</th>
                    <th className="text-right px-3 py-2 font-medium">Outstanding</th>
                    <th className="text-left px-3 py-2 font-medium">Oldest due</th>
                    <th className="text-right px-3 py-2 font-medium">Overdue</th>
                    <th className="px-3 py-2" />
                  </tr>
                </thead>
                <tbody>
                  {defaulters.map((d) => (
                    <tr key={d.studentId} className="border-t border-slate-100 hover:bg-slate-50">
                      <td className="px-3 py-2 font-medium text-slate-800">{d.studentName}</td>
                      <td className="px-3 py-2 text-slate-500">{d.sectionName}</td>
                      <td className="px-3 py-2 text-right font-semibold text-amber-700">
                        {formatINR(d.outstandingPaise)}
                      </td>
                      <td className="px-3 py-2 text-slate-500 text-xs">{formatDate(d.oldestDueDate)}</td>
                      <td className="px-3 py-2 text-right">
                        {d.daysOverdue > 0 ? (
                          <span className="text-red-600 font-medium">{d.daysOverdue}d</span>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        <Link
                          href={`/tenants/${tenantId}/fees/collect?studentId=${d.studentId}`}
                          className="text-primary hover:underline text-xs inline-flex items-center gap-0.5"
                        >
                          Collect <ChevronRight size={11} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </CardBody>
    </Card>
  );
}

// ───────────────────────────────────────────────────────────────────────────
// Defaulters Tab
// ───────────────────────────────────────────────────────────────────────────

function DefaultersTab({
  tenantId, defaulters, loading, error, onRetry,
}: {
  tenantId: string;
  defaulters: DefaulterResponse[];
  loading: boolean;
  error: unknown;
  onRetry: () => void;
}) {
  const [classFilter, setClassFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState<'outstanding' | 'overdue'>('outstanding');

  const classNames = useMemo(
    () => Array.from(new Set(defaulters.map((d) => d.className))).sort(),
    [defaulters],
  );

  const filtered = useMemo(() => {
    let list = classFilter === 'ALL' ? defaulters : defaulters.filter((d) => d.className === classFilter);
    if (sortBy === 'outstanding') return [...list].sort((a, b) => b.outstandingPaise - a.outstandingPaise);
    return [...list].sort((a, b) => b.daysOverdue - a.daysOverdue);
  }, [defaulters, classFilter, sortBy]);

  if (loading) {
    return (
      <div className="space-y-3">
        {[1,2,3].map((i) => <Skeleton key={i} className="h-12 w-full" />)}
      </div>
    );
  }
  if (error) return <ErrorBanner error={error} onRetry={onRetry} />;

  if (defaulters.length === 0) {
    return (
      <div className="bg-white border border-slate-200 rounded-xl p-12 text-center">
        <CheckCircle2 size={32} className="mx-auto mb-2 text-green-500" />
        <p className="text-slate-700 font-semibold">No outstanding fees! 🎉</p>
        <p className="text-sm text-slate-400 mt-1">All students are up to date with their payments.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-slate-500">Class:</span>
          <select
            value={classFilter}
            onChange={(e) => setClassFilter(e.target.value)}
            className="text-sm border border-slate-300 rounded-lg px-2 py-1 bg-white focus:outline-none focus:ring-1 focus:ring-primary"
          >
            <option value="ALL">All classes</option>
            {classNames.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-slate-500">Sort by:</span>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as 'outstanding' | 'overdue')}
            className="text-sm border border-slate-300 rounded-lg px-2 py-1 bg-white focus:outline-none focus:ring-1 focus:ring-primary"
          >
            <option value="outstanding">Highest outstanding</option>
            <option value="overdue">Most overdue</option>
          </select>
        </div>
        <span className="ml-auto text-xs text-slate-400">
          {filtered.length} student{filtered.length !== 1 ? 's' : ''}
        </span>
      </div>

      <Card className="p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium">Student</th>
                <th className="text-left px-4 py-2.5 font-medium">Class · Section</th>
                <th className="text-right px-4 py-2.5 font-medium">Outstanding</th>
                <th className="text-left px-4 py-2.5 font-medium">Oldest due date</th>
                <th className="text-right px-4 py-2.5 font-medium">Days overdue</th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody>
              {filtered.map((d) => (
                <tr key={d.studentId} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5">
                    <Link
                      href={`/tenants/${tenantId}/students/${d.studentId}`}
                      className="font-medium text-slate-800 hover:text-primary"
                    >
                      {d.studentName}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5 text-slate-500">{d.className} · {d.sectionName}</td>
                  <td className="px-4 py-2.5 text-right font-semibold text-amber-700">
                    {formatINR(d.outstandingPaise)}
                  </td>
                  <td className="px-4 py-2.5 text-slate-500 text-xs">
                    {d.oldestDueDate ? formatDate(d.oldestDueDate) : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    {d.daysOverdue > 30 ? (
                      <Badge tone="danger" size="sm">{d.daysOverdue}d</Badge>
                    ) : d.daysOverdue > 0 ? (
                      <Badge tone="warning" size="sm">{d.daysOverdue}d</Badge>
                    ) : (
                      <span className="text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-2.5">
                    <Link
                      href={`/tenants/${tenantId}/fees/collect?studentId=${d.studentId}`}
                      className="text-primary hover:underline text-xs inline-flex items-center gap-0.5"
                    >
                      Collect <ChevronRight size={11} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

// ───────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ───────────────────────────────────────────────────────────────────────────

function KpiTile({ icon, label, value, sub, border, valueClass }: {
  icon: React.ReactNode; label: string; value: string; sub?: string;
  border?: string; valueClass?: string;
}) {
  return (
    <div className={cn('bg-white rounded-xl border p-4 flex flex-col gap-1', border ?? 'border-slate-200')}>
      <div className="flex items-center gap-1.5 text-xs uppercase text-slate-500 tracking-wide">
        {icon}{label}
      </div>
      <div className={cn('text-2xl font-semibold mt-0.5', valueClass ?? 'text-slate-900')}>{value}</div>
      {sub && <div className="text-xs text-slate-500">{sub}</div>}
    </div>
  );
}

function NavLink({ href, label, icon, accent }: {
  href: string; label: string; icon: React.ReactNode; accent?: boolean;
}) {
  return (
    <Link
      href={href}
      className={cn('flex items-center gap-2 py-1.5 hover:underline', accent ? 'text-red-600' : 'text-primary')}
    >
      {icon}{label}<ArrowRight size={12} className="ml-auto" />
    </Link>
  );
}

function RecentPaymentsTable({ payments, tenantId }: { payments: RecentPaymentRow[]; tenantId: string }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="text-left px-4 py-2 font-medium">Student</th>
            <th className="text-left px-4 py-2 font-medium">Date</th>
            <th className="text-left px-4 py-2 font-medium">Mode</th>
            <th className="text-right px-4 py-2 font-medium">Amount</th>
            <th className="text-left px-4 py-2 font-medium">Receipt</th>
          </tr>
        </thead>
        <tbody>
          {payments.map((p) => (
            <tr key={p.paymentId} className="border-t border-slate-100 hover:bg-slate-50">
              <td className="px-4 py-2">
                <Link
                  href={`/tenants/${tenantId}/students/${p.studentId}`}
                  className="font-medium text-slate-800 hover:text-primary"
                >
                  {p.studentName}
                </Link>
              </td>
              <td className="px-4 py-2 text-slate-500">{formatDate(p.paymentDate)}</td>
              <td className="px-4 py-2">
                <Badge size="sm" tone={p.paymentMode === 'CASH' ? 'neutral' : 'info'}>
                  {p.paymentMode}
                </Badge>
              </td>
              <td className="px-4 py-2 text-right font-semibold">{formatINR(p.amountPaise)}</td>
              <td className="px-4 py-2">
                {p.receiptPdfUrl ? (
                  <a
                    href={p.receiptPdfUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-primary hover:underline inline-flex items-center gap-0.5"
                  >
                    {p.receiptNumber} <ExternalLink size={11} />
                  </a>
                ) : (
                  <span className="text-slate-500">{p.receiptNumber}</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
