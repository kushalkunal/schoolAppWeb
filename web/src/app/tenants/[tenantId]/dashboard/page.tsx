'use client';

import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import {
  Users, CalendarCheck2, Wallet, AlertTriangle, TrendingUp, TrendingDown,
  Minus, Sparkles, ArrowUpRight, Bot, GraduationCap, Activity,
} from 'lucide-react';
import { apiGet } from '@/api/client';
import { isApiError, hasCode } from '@/api/errors';
import { riskApi } from '@/api/endpoints/risk';
import { admissionsApi } from '@/api/endpoints/admissions';
import { useAuth } from '@/auth/AuthProvider';
import { useBranding } from '@/brand/BrandingProvider';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Stat } from '@/components/ui/Stat';
import { Badge } from '@/components/ui/Badge';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton, SkeletonCard } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';

interface DashboardResponse {
  asOfDate: string;
  attendance: { totalMarked: number; present: number; absent: number; late: number; halfDay: number; leave: number };
  alerts: { total: number; high: number; critical: number };
  fees: { mtdCollectedPaise: number; activeAtRiskCount: number };
  unmarkedSectionsCount: number;
  topAtRisk: Array<{ studentId: string; studentName: string; score: number; topFactor: string; marksTrend?: string }>;
}

/**
 * Premium principal dashboard. Single-screen overview blending:
 *
 * 1. Branded welcome card with school name + today's date.
 * 2. KPI tile grid — attendance / unmarked / fees / alerts (Stat component).
 * 3. AI at-risk panel — top scored students with their LLM-written narratives
 *    (fetched from the new /risk/students endpoint, not the dashboard's truncated rows).
 * 4. Recent admissions funnel — last 5 enquiries / applications.
 * 5. Attendance breakdown — donut summary.
 *
 * Each panel degrades independently — if the AI feature flag is off, the at-risk panel
 * shows the templated fallback narrative; if admissions is off it just hides.
 */
export default function DashboardPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const { state } = useAuth();
  const b = useBranding();
  const firstName = state.status === 'authenticated' ? state.claims.name.split(' ')[0] : '';
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

  const dashQ = useQuery<DashboardResponse>({
    queryKey: ['dashboard', tenantId],
    queryFn: () => apiGet<DashboardResponse>(`/api/v1/tenants/${tenantId}/dashboard`),
    enabled: !!tenantId,
    staleTime: 60_000,
  });

  const riskQ = useQuery({
    queryKey: ['risk', tenantId, 'top'],
    queryFn: () => riskApi.list(tenantId, { minScore: 60, size: 4 }),
    enabled: !!tenantId,
    retry: false,
  });

  const admissionsQ = useQuery({
    queryKey: ['admissions', tenantId, 'recent'],
    queryFn: () => admissionsApi.list(tenantId, { size: 5 }),
    enabled: !!tenantId,
    retry: false,
  });

  // ---------------- Loading ----------------
  if (dashQ.isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-28 w-full" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      </div>
    );
  }

  // ---------------- Error ----------------
  if (dashQ.isError) {
    if (hasCode(dashQ.error, 'FEATURE_DISABLED')) {
      return (
        <EmptyState
          icon={<Sparkles size={28} />}
          title="Analytics isn't enabled for your plan"
          description="Upgrade to unlock the principal dashboard, KPI tiles, and AI insights. Talk to your administrator."
        />
      );
    }
    return <ErrorBanner error={dashQ.error} onRetry={() => dashQ.refetch()} />;
  }

  const d = dashQ.data!;
  const present = d.attendance.present;
  const totalMarked = d.attendance.totalMarked;
  const attendancePct = totalMarked > 0 ? Math.round((present * 100) / totalMarked) : 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title={`${greeting}${firstName ? ', ' + firstName : ''}`}
        description={
          <span>
            Here's what's happening at{' '}
            <span className="font-medium text-slate-700">{b.schoolName}</span> ·{' '}
            {new Date(d.asOfDate).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
          </span>
        }
      />

      {/* ---------------- KPI tiles ---------------- */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Stat
          label="Present today"
          value={`${present}/${totalMarked}`}
          hint={`${attendancePct}% attendance`}
          icon={<Users size={18} />}
          tone="primary"
        />
        <Stat
          label="Unmarked sections"
          value={d.unmarkedSectionsCount}
          hint={d.unmarkedSectionsCount === 0 ? 'All sections marked' : 'Awaiting class teachers'}
          icon={<CalendarCheck2 size={18} />}
          tone={d.unmarkedSectionsCount > 0 ? 'warning' : 'success'}
        />
        <Stat
          label="Collection (MTD)"
          value={`₹${formatLakh(d.fees.mtdCollectedPaise)}`}
          hint={`${d.fees.activeAtRiskCount} students at risk`}
          icon={<Wallet size={18} />}
          tone="accent"
        />
        <Stat
          label="Open alerts"
          value={d.alerts.total}
          hint={d.alerts.critical > 0 ? `${d.alerts.critical} critical` : 'No critical alerts'}
          icon={<AlertTriangle size={18} />}
          tone={d.alerts.critical > 0 ? 'danger' : d.alerts.high > 0 ? 'warning' : 'info'}
        />
      </div>

      {/* ---------------- Three-up grid ---------------- */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* AI at-risk panel — flagship */}
        <Card className="lg:col-span-2 p-0 overflow-hidden">
          <CardHeader className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-7 h-7 grid place-items-center rounded-brand bg-brand-gradient text-primary-fg">
                <Bot size={14} />
              </span>
              <div>
                <CardTitle>Students who need attention</CardTitle>
                <CardDescription>AI-summarised risk across attendance, fees, and marks</CardDescription>
              </div>
            </div>
            <Link href={`/tenants/${tenantId}/risk`} className="text-xs text-primary font-medium hover:underline inline-flex items-center gap-1">
              View all <ArrowUpRight size={12} />
            </Link>
          </CardHeader>
          <CardBody className="space-y-3">
            {riskQ.isLoading && <Skeleton className="h-24 w-full" />}
            {riskQ.isError && (
              <p className="text-sm text-slate-500">
                {hasCode(riskQ.error, 'FEATURE_DISABLED')
                  ? 'AI risk-scoring is part of the Enterprise plan.'
                  : isApiError(riskQ.error) ? riskQ.error.message : 'Could not load risk scores.'}
              </p>
            )}
            {riskQ.data && riskQ.data.length === 0 && (
              <p className="text-sm text-slate-500">No students are flagged this week. Nice work.</p>
            )}
            {riskQ.data && riskQ.data.length > 0 && riskQ.data.map((r) => (
              <AtRiskCard key={r.id} tenantId={tenantId} score={r.score}
                studentId={r.studentId}
                topFactor={r.topFactor} summary={r.summary} trend={r.marksTrend} />
            ))}

            {/* Fallback: dashboard's own top-at-risk rows if risk endpoint isn't on plan */}
            {riskQ.isError && d.topAtRisk.length > 0 && (
              <div className="border-t pt-3 mt-3 space-y-2">
                {d.topAtRisk.map((s) => (
                  <div key={s.studentId} className="flex items-center justify-between text-sm">
                    <Link href={`/tenants/${tenantId}/students/${s.studentId}`}
                          className="font-medium text-slate-800 hover:text-primary">{s.studentName}</Link>
                    <div className="flex items-center gap-2">
                      <Badge tone="warning" size="sm">{s.topFactor}</Badge>
                      <span className="text-xs font-semibold text-slate-700">{s.score}/100</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardBody>
        </Card>

        {/* Attendance breakdown */}
        <Card className="p-0 overflow-hidden">
          <CardHeader>
            <CardTitle>Attendance today</CardTitle>
            <CardDescription>{attendancePct}% present out of marked students</CardDescription>
          </CardHeader>
          <CardBody>
            <AttendanceBar
              present={d.attendance.present}
              absent={d.attendance.absent}
              late={d.attendance.late}
              halfDay={d.attendance.halfDay}
              leave={d.attendance.leave}
            />
            <div className="mt-4 space-y-1.5 text-sm">
              <Row label="Present"  value={d.attendance.present} tone="success" />
              <Row label="Absent"   value={d.attendance.absent}  tone="danger" />
              <Row label="Late"     value={d.attendance.late}    tone="warning" />
              <Row label="Half-day" value={d.attendance.halfDay} tone="info" />
              <Row label="On leave" value={d.attendance.leave}   tone="neutral" />
            </div>
          </CardBody>
        </Card>
      </div>

      {/* ---------------- Recent admissions + quick actions ---------------- */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2 p-0 overflow-hidden">
          <CardHeader className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-7 h-7 grid place-items-center rounded-brand bg-accent-soft text-accent">
                <GraduationCap size={14} />
              </span>
              <div>
                <CardTitle>Recent admissions</CardTitle>
                <CardDescription>Latest enquiries and applications</CardDescription>
              </div>
            </div>
            <Link href={`/tenants/${tenantId}/admissions`} className="text-xs text-primary font-medium hover:underline inline-flex items-center gap-1">
              Open funnel <ArrowUpRight size={12} />
            </Link>
          </CardHeader>
          <CardBody>
            {admissionsQ.isError && hasCode(admissionsQ.error, 'FEATURE_DISABLED') && (
              <p className="text-sm text-slate-500">Admissions funnel is part of the Enterprise plan.</p>
            )}
            {admissionsQ.isError && !hasCode(admissionsQ.error, 'FEATURE_DISABLED') && (
              <p className="text-sm text-slate-500">
                {isApiError(admissionsQ.error) ? admissionsQ.error.message : 'Could not load admissions.'}
              </p>
            )}
            {admissionsQ.isLoading && <Skeleton className="h-24 w-full" />}
            {admissionsQ.data && admissionsQ.data.items.length === 0 && (
              <p className="text-sm text-slate-500">No admissions yet.</p>
            )}
            {admissionsQ.data && admissionsQ.data.items.length > 0 && (
              <div className="divide-y divide-slate-100 -mx-1">
                {admissionsQ.data.items.map((a) => (
                  <div key={a.id} className="flex items-center justify-between py-2.5 px-1">
                    <div className="min-w-0">
                      <Link href={`/tenants/${tenantId}/admissions/${a.id}`} className="text-sm font-medium text-slate-800 hover:text-primary truncate block">
                        {a.studentDisplayName}
                      </Link>
                      <div className="text-xs text-slate-500 truncate">
                        {a.intendedClass}
                        {a.intendedSection ? ` · Section ${a.intendedSection}` : ''}
                        {a.source ? ` · ${a.source}` : ''}
                      </div>
                    </div>
                    <AdmissionStatusBadge status={a.status} />
                  </div>
                ))}
              </div>
            )}
          </CardBody>
        </Card>

        <Card className="p-0 overflow-hidden">
          <CardHeader>
            <CardTitle>Quick actions</CardTitle>
          </CardHeader>
          <CardBody className="space-y-2">
            <QuickAction href={`/tenants/${tenantId}/attendance`}      icon={<CalendarCheck2 size={14} />} label="Mark attendance" />
            <QuickAction href={`/tenants/${tenantId}/fees/collect`}    icon={<Wallet size={14} />}        label="Collect fee" />
            <QuickAction href={`/tenants/${tenantId}/students`}        icon={<Users size={14} />}         label="Add student" />
            <QuickAction href={`/tenants/${tenantId}/circulars`}       icon={<Activity size={14} />}      label="Send circular" />
            <QuickAction href={`/tenants/${tenantId}/academics/exams`} icon={<GraduationCap size={14} />} label="Enter marks" />
          </CardBody>
        </Card>
      </div>
    </div>
  );
}

// ------------------------- Helpers -------------------------

function formatLakh(paise: number): string {
  const rupees = paise / 100;
  if (rupees >= 1e7) return (rupees / 1e7).toFixed(2) + ' Cr';
  if (rupees >= 1e5) return (rupees / 1e5).toFixed(2) + ' L';
  return rupees.toLocaleString('en-IN');
}

function AtRiskCard({
  tenantId, studentId, score, topFactor, summary, trend,
}: { tenantId: string; studentId: string; score: number; topFactor: string | null; summary: string | null; trend: string | null }) {
  const TrendIcon = trend === 'DOWN' ? TrendingDown : trend === 'UP' ? TrendingUp : Minus;
  const trendColor = trend === 'DOWN' ? 'text-danger' : trend === 'UP' ? 'text-success' : 'text-slate-500';
  return (
    <Link
      href={`/tenants/${tenantId}/students/${studentId}`}
      className="block rounded-brand border border-slate-200 hover:border-primary hover:shadow-sm p-3 transition-all"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <Badge tone="danger" size="sm">Risk {score}/100</Badge>
            {topFactor && <Badge tone="warning" size="sm">{topFactor}</Badge>}
            {trend && trend !== 'UNKNOWN' && (
              <span className={`inline-flex items-center gap-0.5 text-[11px] ${trendColor}`}>
                <TrendIcon size={11} /> {trend.toLowerCase()}
              </span>
            )}
          </div>
          {summary && (
            <p className="text-sm text-slate-700 mt-1.5 line-clamp-2 leading-snug">{summary}</p>
          )}
        </div>
        <ArrowUpRight size={14} className="text-slate-400 shrink-0" />
      </div>
    </Link>
  );
}

function AttendanceBar({ present, absent, late, halfDay, leave }: {
  present: number; absent: number; late: number; halfDay: number; leave: number;
}) {
  const total = present + absent + late + halfDay + leave;
  if (total === 0) return <div className="h-3 rounded-full bg-slate-100" />;
  const pct = (n: number) => (n / total) * 100;
  return (
    <div className="h-3 rounded-full overflow-hidden flex bg-slate-100">
      <span style={{ width: `${pct(present)}%` }} className="bg-success" />
      <span style={{ width: `${pct(late)}%` }}    className="bg-warning" />
      <span style={{ width: `${pct(halfDay)}%` }} className="bg-info" />
      <span style={{ width: `${pct(leave)}%` }}   className="bg-slate-400" />
      <span style={{ width: `${pct(absent)}%` }}  className="bg-danger" />
    </div>
  );
}

function Row({ label, value, tone }:
  { label: string; value: number; tone: 'success' | 'danger' | 'warning' | 'info' | 'neutral' }) {
  return (
    <div className="flex justify-between items-center">
      <div className="flex items-center gap-2">
        <Badge tone={tone} size="sm" dot>{label}</Badge>
      </div>
      <span className="font-semibold text-slate-800 tabular-nums">{value}</span>
    </div>
  );
}

function QuickAction({ href, icon, label }: { href: string; icon: React.ReactNode; label: string }) {
  return (
    <Link
      href={href}
      className="flex items-center justify-between p-2.5 rounded-brand hover:bg-primary-soft text-sm text-slate-700 hover:text-primary transition group"
    >
      <span className="inline-flex items-center gap-2 font-medium">
        <span className="w-7 h-7 grid place-items-center rounded-brand bg-slate-100 text-slate-600 group-hover:bg-primary group-hover:text-primary-fg transition-colors">
          {icon}
        </span>
        {label}
      </span>
      <ArrowUpRight size={14} className="text-slate-400 group-hover:text-primary" />
    </Link>
  );
}

function AdmissionStatusBadge({ status }: { status: string }) {
  const map: Record<string, 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info'> = {
    ENQUIRY: 'neutral',
    APPLICATION_SUBMITTED: 'info',
    TEST_SCHEDULED: 'warning',
    TEST_COMPLETED: 'warning',
    OFFERED: 'primary',
    ACCEPTED: 'accent',
    DECLINED: 'danger',
    ENROLLED: 'success',
    WITHDRAWN: 'neutral',
    REJECTED: 'danger',
  };
  return <Badge tone={map[status] ?? 'neutral'} size="sm">{status.replace(/_/g, ' ').toLowerCase()}</Badge>;
}
