'use client';

import { useQuery } from '@tanstack/react-query';
import { useMemo, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  Users, CalendarCheck2, Wallet, AlertTriangle, TrendingUp, TrendingDown,
  Minus, Sparkles, ArrowUpRight, Bot, GraduationCap, Activity, CheckCircle2, Clock, BookOpen,
  UserCog, Plane,
} from 'lucide-react';
import { homeworkApi } from '@/api/endpoints/homework';
import { apiGet } from '@/api/client';
import { isApiError, hasCode } from '@/api/errors';
import { riskApi } from '@/api/endpoints/risk';
import { admissionsApi } from '@/api/endpoints/admissions';
import { attendanceApi } from '@/api/endpoints/attendance';
import { schoolApi } from '@/api/endpoints/school';
import { teacherAssignmentsApi } from '@/api/endpoints/teacherAssignments';
import { academicsApi } from '@/api/endpoints/academics';
import { timetableApi } from '@/api/endpoints/timetable';
import { substitutesApi } from '@/api/endpoints/substitutes';
import { useAuth } from '@/auth/AuthProvider';
import { useBranding } from '@/brand/BrandingProvider';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Stat } from '@/components/ui/Stat';
import { Badge } from '@/components/ui/Badge';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton, SkeletonCard } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { todayIso } from '@/lib/utils';
import type { SectionResponse, SubjectResponse, TeacherAssignmentResponse, PeriodResponse, TimetableEntryResponse, SubstituteResponse } from '@/types/domain';

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
  const router = useRouter();
  const firstName = state.status === 'authenticated' ? (state.claims.name.split(' ')[0] ?? '') : '';
  const role = state.status === 'authenticated' ? state.claims.role : '';
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

  // Accountant: redirect to fee dashboard (their home)
  useEffect(() => {
    if (role === 'ACCOUNTANT') router.replace(`/tenants/${tenantId}/fees/dashboard`);
    if (role === 'LIBRARIAN') router.replace(`/tenants/${tenantId}/library/books`);
  }, [role, tenantId, router]);

  // Teacher roles get a simplified dashboard
  if (role === 'CLASS_TEACHER' || role === 'SUBJECT_TEACHER') {
    return <TeacherDashboard tenantId={tenantId} firstName={firstName} greeting={greeting} />;
  }
  if (role === 'ACCOUNTANT' || role === 'LIBRARIAN') return null;

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

  // Today's homework posted by teachers
  const homeworkQ = useQuery({
    queryKey: ['homework', tenantId, 'all'],
    queryFn: () => homeworkApi.listAssignments(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  // All staff for teacher-name lookup in the homework widget
  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
    retry: false,
    staleTime: 5 * 60_000,
  });

  const todayHomework = useMemo(() => {
    if (!homeworkQ.data) return [];
    const today = new Date().toISOString().slice(0, 10);
    return homeworkQ.data.filter((a) => a.createdAt.slice(0, 10) === today);
  }, [homeworkQ.data]);

  const staffMap = useMemo(() => {
    const map = new Map<string, string>();
    (staffQ.data ?? []).forEach((s) => map.set(s.id, s.displayName));
    return map;
  }, [staffQ.data]);

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

  // Defensive normalisation: a freshly-onboarded school (or a partial API payload) can return
  // a dashboard with missing sub-objects. Default every block so the page renders an empty
  // overview instead of crashing on `undefined.present`.
  const raw = dashQ.data;
  if (!raw) {
    return (
      <div className="space-y-6">
        <PageHeader title="Dashboard" description="Today's overview" />
        <EmptyState
          icon={<Activity />}
          title="Nothing to show yet"
          description="Once attendance, fees and admissions data start flowing in, your daily overview will appear here."
        />
      </div>
    );
  }
  const d = {
    asOfDate: raw.asOfDate,
    attendance: raw.attendance ?? { totalMarked: 0, present: 0, absent: 0, late: 0, halfDay: 0, leave: 0 },
    alerts: raw.alerts ?? { total: 0, high: 0, critical: 0 },
    fees: raw.fees ?? { mtdCollectedPaise: 0, activeAtRiskCount: 0 },
    unmarkedSectionsCount: raw.unmarkedSectionsCount ?? 0,
    topAtRisk: raw.topAtRisk ?? [],
  };
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

      {/* ---------------- Today's homework widget ---------------- */}
      <Card className="p-0 overflow-hidden">
        <CardHeader className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-7 h-7 grid place-items-center rounded-brand bg-primary-soft text-primary">
              <BookOpen size={14} />
            </span>
            <div>
              <CardTitle>Today's homework</CardTitle>
              <CardDescription>Assignments posted by teachers today</CardDescription>
            </div>
          </div>
          <Link href={`/tenants/${tenantId}/homework`} className="text-xs text-primary font-medium hover:underline inline-flex items-center gap-1">
            View all <ArrowUpRight size={12} />
          </Link>
        </CardHeader>
        <CardBody>
          {homeworkQ.isLoading && <Skeleton className="h-20 w-full" />}
          {!homeworkQ.isLoading && todayHomework.length === 0 && (
            <p className="text-sm text-slate-500">No homework posted today yet.</p>
          )}
          {todayHomework.length > 0 && (
            <div className="divide-y divide-slate-100 -mx-1">
              {todayHomework.map((a: import('@/api/endpoints/homework').AssignmentDto) => {
                const teacherName = a.createdByStaffId ? (staffMap.get(a.createdByStaffId) ?? 'Unknown teacher') : 'Unknown teacher';
                const snippet = a.body.length > 80 ? a.body.slice(0, 80) + '…' : a.body;
                return (
                  <div key={a.id} className="py-3 px-1">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-slate-800 truncate">{a.title}</p>
                        <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{snippet}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 mt-1.5 text-xs text-slate-500">
                      <Badge tone="info" size="sm">{teacherName}</Badge>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </CardBody>
      </Card>
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

// ------------------------- Teacher Dashboard -------------------------

/** Convert JS getDay() (0=Sun…6=Sat) to ISO weekday (1=Mon…7=Sun) */
function jsToIsoDay(jsDay: number): number {
  return jsDay === 0 ? 7 : jsDay;
}

/** Parse "HH:MM" → total minutes since midnight */
function timeToMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

function TeacherDashboard({ tenantId, firstName, greeting }: {
  tenantId: string;
  firstName: string;
  greeting: string;
}) {
  const { state } = useAuth();
  const staffId = state.status === 'authenticated' ? state.claims.sub : '';
  const role = state.status === 'authenticated' ? state.claims.role : '';
  const today = todayIso();
  const todayIsoDay = jsToIsoDay(new Date().getDay());
  const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();

  // My assigned sections (class teacher)
  const sectionsQ = useQuery<SectionResponse[]>({
    queryKey: ['sections-mine', tenantId],
    queryFn: () => schoolApi.myAssignedSections(tenantId),
    enabled: !!tenantId,
  });

  // All classes to resolve class names
  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  // Today's unmarked sections to know what's still pending
  const unmarkedQ = useQuery({
    queryKey: ['attendance-unmarked', tenantId, today],
    queryFn: () => attendanceApi.unmarked(tenantId, today),
    enabled: !!tenantId,
  });

  // My timetable entries across all sections
  const timetableQ = useQuery<TimetableEntryResponse[]>({
    queryKey: ['teacher-timetable', tenantId, staffId],
    queryFn: () => timetableApi.getTeacherTimetable(tenantId, staffId),
    enabled: !!tenantId && !!staffId,
    retry: false,
  });

  // Period definitions (for times)
  const periodsQ = useQuery<PeriodResponse[]>({
    queryKey: ['periods', tenantId],
    queryFn: () => timetableApi.listPeriods(tenantId),
    enabled: !!tenantId,
  });

  // Subjects (for names)
  const subjectsQ = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  // Today's substitutes — to show substitute duty
  const substitutesQ = useQuery<SubstituteResponse[]>({
    queryKey: ['substitutes', tenantId, today],
    queryFn: () => substitutesApi.listForDate(tenantId, today),
    enabled: !!tenantId,
    retry: false,
  });

  // Subject teacher assignments
  const assignmentsQ = useQuery<TeacherAssignmentResponse[]>({
    queryKey: ['teacher-assignments', tenantId, staffId],
    queryFn: () => teacherAssignmentsApi.list(tenantId, staffId),
    enabled: !!tenantId && role === 'SUBJECT_TEACHER',
    retry: false,
  });

  const classNameMap = new Map<string, string>();
  if (classesQ.data) {
    for (const cls of classesQ.data) {
      for (const sec of cls.sections) {
        classNameMap.set(sec.id, `${cls.name} – ${sec.name}`);
      }
    }
  }

  const periodMap = new Map<string, PeriodResponse>(
    (periodsQ.data ?? []).map((p) => [p.id, p])
  );

  const subjectMap = new Map<string, string>(
    (subjectsQ.data ?? []).map((s: SubjectResponse) => [s.id, s.name])
  );

  const unmarkedIds = new Set((unmarkedQ.data ?? []).map((u) => u.sectionId));

  // My substitute duties today (I am the substitute)
  const mySubDuties = (substitutesQ.data ?? []).filter((s) => s.substituteId === staffId);

  // Today's teaching schedule: filter timetable entries for today and sort by period order
  const todayEntries = (timetableQ.data ?? [])
    .filter((e) => e.dayOfWeek === todayIsoDay)
    .sort((a, b) => {
      const pA = periodMap.get(a.periodId);
      const pB = periodMap.get(b.periodId);
      return (pA?.sortOrder ?? 0) - (pB?.sortOrder ?? 0);
    });

  // Find current and next period
  let currentEntryId: string | null = null;
  let nextEntryId: string | null = null;
  for (const entry of todayEntries) {
    const p = periodMap.get(entry.periodId);
    if (!p || p.breakSlot) continue;
    const start = timeToMinutes(p.startTime);
    const end = timeToMinutes(p.endTime);
    if (nowMinutes >= start && nowMinutes < end) {
      currentEntryId = entry.id;
    } else if (nowMinutes < start && nextEntryId === null) {
      nextEntryId = entry.id;
    }
  }

  const teachingScheduleReady = timetableQ.data !== undefined && periodsQ.data !== undefined;

  return (
    <div className="space-y-6">
      <PageHeader
        title={`${greeting}${firstName ? ', ' + firstName : ''}`}
        description="Here’s your schedule and class summary for today."
      />

      {/* ---- Substitute duty alert ---- */}
      {mySubDuties.length > 0 && (
        <div className="rounded-brand border border-warning/30 bg-warning/10 px-4 py-3 flex items-start gap-3">
          <AlertTriangle size={18} className="text-warning mt-0.5 shrink-0" />
          <div className="text-sm">
            <p className="font-semibold text-slate-800 mb-1">You have substitute duties today</p>
            <ul className="space-y-0.5 text-slate-600">
              {mySubDuties.map((s) => (
                <li key={s.id}>
                  {classNameMap.get(s.sectionId) ?? s.sectionId}
                  {s.note ? <span className="text-slate-500"> · {s.note}</span> : null}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* ---- Today's Schedule ---- */}
      <Card className="p-0 overflow-hidden">
        <CardHeader>
          <div className="flex items-center gap-2">
            <span className="w-7 h-7 grid place-items-center rounded-brand bg-primary-soft text-primary">
              <Clock size={14} />
            </span>
            <div>
              <CardTitle>Today’s Schedule</CardTitle>
              <CardDescription>
                {new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardBody>
          {(timetableQ.isLoading || periodsQ.isLoading) && <Skeleton className="h-32 w-full" />}
          {timetableQ.isError && (
            <p className="text-sm text-slate-500">Could not load your timetable.</p>
          )}
          {teachingScheduleReady && todayEntries.length === 0 && (
            <p className="text-sm text-slate-500">No classes scheduled for today.</p>
          )}
          {teachingScheduleReady && todayEntries.length > 0 && (
            <div className="space-y-2">
              {todayEntries.map((entry) => {
                const period = periodMap.get(entry.periodId);
                if (!period) return null;
                const isCurrent = entry.id === currentEntryId;
                const isNext = entry.id === nextEntryId;
                const sectionName = classNameMap.get(entry.sectionId) ?? entry.sectionId;
                const subjectName = entry.subjectId ? (subjectMap.get(entry.subjectId) ?? 'Unknown subject') : null;
                return (
                  <div
                    key={entry.id}
                    className={`flex items-center gap-3 rounded-brand border px-3 py-2.5 text-sm transition ${
                      isCurrent ? 'border-primary bg-primary-soft' : 'border-slate-200 bg-white'
                    }`}
                  >
                    <div className="w-20 shrink-0 text-xs text-slate-500 font-mono">
                      {period.startTime.slice(0, 5)}–{period.endTime.slice(0, 5)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <span className="font-semibold text-slate-800">{sectionName}</span>
                      {subjectName && (
                        <span className="text-slate-500 ml-2">· {subjectName}</span>
                      )}
                    </div>
                    {isCurrent && <Badge tone="primary" size="sm">NOW</Badge>}
                    {isNext && !isCurrent && <Badge tone="info" size="sm">NEXT</Badge>}
                  </div>
                );
              })}
            </div>
          )}
        </CardBody>
      </Card>

      {/* ---- Class Teacher: my sections ---- */}
      {role === 'CLASS_TEACHER' && (
        <div>
          <h2 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-3">My Classes</h2>
          {sectionsQ.isLoading && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <SkeletonCard /><SkeletonCard />
            </div>
          )}
          {sectionsQ.isError && <ErrorBanner error={sectionsQ.error} onRetry={() => sectionsQ.refetch()} />}
          {sectionsQ.data && sectionsQ.data.length === 0 && mySubDuties.length === 0 && (
            <EmptyState
              icon={<Users size={24} />}
              title="No sections assigned"
              description="Ask your admin to assign you as a class teacher for a section."
            />
          )}
          {sectionsQ.data && sectionsQ.data.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {sectionsQ.data.map((section) => {
                const isUnmarked = unmarkedIds.has(section.id);
                return (
                  <Card key={section.id} className="p-0 overflow-hidden">
                    <CardHeader className="flex items-center justify-between">
                      <div>
                        <CardTitle>{classNameMap.get(section.id) ?? section.name}</CardTitle>
                        <CardDescription>
                          {isUnmarked
                            ? <span className="flex items-center gap-1 text-warning"><Clock size={12} /> Attendance not marked</span>
                            : <span className="flex items-center gap-1 text-success"><CheckCircle2 size={12} /> Attendance marked</span>
                          }
                        </CardDescription>
                      </div>
                    </CardHeader>
                    <CardBody>
                      <Link
                        href={`/tenants/${tenantId}/attendance/${section.id}`}
                        className="flex items-center justify-center gap-2 w-full py-2 px-4 rounded-brand text-sm font-medium bg-primary text-primary-fg hover:opacity-90 transition"
                      >
                        <CalendarCheck2 size={14} />
                        {isUnmarked ? 'Mark Attendance' : 'View Attendance'}
                      </Link>
                    </CardBody>
                  </Card>
                );
              })}
            </div>
          )}
          {/* Substitute sections for today */}
          {mySubDuties.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-3">
              {mySubDuties.map((s) => {
                const isUnmarked = unmarkedIds.has(s.sectionId);
                return (
                  <Card key={s.id} className="p-0 overflow-hidden border-warning/50">
                    <CardHeader className="flex items-center justify-between">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <CardTitle>{classNameMap.get(s.sectionId) ?? s.sectionId}</CardTitle>
                          <Badge tone="warning" size="sm">Substitute</Badge>
                        </div>
                        <CardDescription>
                          {isUnmarked
                            ? <span className="flex items-center gap-1 text-warning"><Clock size={12} /> Attendance not marked</span>
                            : <span className="flex items-center gap-1 text-success"><CheckCircle2 size={12} /> Attendance marked</span>
                          }
                        </CardDescription>
                      </div>
                    </CardHeader>
                    <CardBody>
                      <Link
                        href={`/tenants/${tenantId}/attendance/${s.sectionId}`}
                        className="flex items-center justify-center gap-2 w-full py-2 px-4 rounded-brand text-sm font-medium bg-warning text-white hover:opacity-90 transition"
                      >
                        <CalendarCheck2 size={14} />
                        {isUnmarked ? 'Mark Attendance' : 'View Attendance'}
                      </Link>
                    </CardBody>
                  </Card>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* ---- Subject Teacher: subject assignments ---- */}
      {role === 'SUBJECT_TEACHER' && (
        <div>
          <h2 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-3">My Subject Assignments</h2>
          {assignmentsQ.isLoading && <SkeletonCard />}
          {assignmentsQ.isError && (
            <p className="text-sm text-slate-500">
              Could not load subject assignments. Ask your admin to assign you to sections.
            </p>
          )}
          {assignmentsQ.data && assignmentsQ.data.length === 0 && (
            <EmptyState
              icon={<GraduationCap size={24} />}
              title="No subject assignments"
              description="Ask your admin to assign you as a subject teacher for a section."
            />
          )}
          {assignmentsQ.data && assignmentsQ.data.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {assignmentsQ.data.map((a) => (
                <Card key={a.id} className="p-0 overflow-hidden">
                  <CardHeader>
                    <CardTitle>{classNameMap.get(a.sectionId) ?? a.sectionId}</CardTitle>
                    <CardDescription>{subjectMap.get(a.subjectId) ?? a.subjectId}</CardDescription>
                  </CardHeader>
                  <CardBody>
                    <Link
                      href={`/tenants/${tenantId}/academics`}
                      className="flex items-center gap-2 text-sm text-primary font-medium hover:underline"
                    >
                      <GraduationCap size={14} /> Go to Academics <ArrowUpRight size={12} />
                    </Link>
                  </CardBody>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ---- Quick Actions ---- */}
      <Card className="p-0 overflow-hidden">
        <CardHeader><CardTitle>Quick actions</CardTitle></CardHeader>
        <CardBody className="space-y-2">
          <QuickAction href={`/tenants/${tenantId}/attendance`}      icon={<CalendarCheck2 size={14} />} label="Mark attendance" />
          <QuickAction href={`/tenants/${tenantId}/hr/attendance`}   icon={<UserCog size={14} />}        label="My attendance" />
          <QuickAction href={`/tenants/${tenantId}/hr/leave`}        icon={<Plane size={14} />}          label="Apply for leave" />
          <QuickAction href={`/tenants/${tenantId}/academics`}       icon={<GraduationCap size={14} />} label="Academics" />
          <QuickAction href={`/tenants/${tenantId}/circulars`}       icon={<Activity size={14} />}      label="Circulars" />
        </CardBody>
      </Card>
    </div>
  );
}
