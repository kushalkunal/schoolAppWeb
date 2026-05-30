'use client';

import { useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, User, Phone, Mail, BookOpen,
  CalendarCheck2, TrendingUp, Wallet, Award, CheckCircle2, XCircle,
  Clock, ChevronRight, BadgePercent, Users,
} from 'lucide-react';
import { studentsApi } from '@/api/endpoints/students';
import { attendanceApi } from '@/api/endpoints/attendance';
import { feesApi } from '@/api/endpoints/fees';
import { academicsApi } from '@/api/endpoints/academics';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { cn, formatINR, formatDate, todayIso } from '@/lib/utils';
import type { ExamResponse, AttendanceStatus, ExamResultResponse } from '@/types/domain';

// ── helpers ──────────────────────────────────────────────────────────────────

function daysBefore(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

const STATUS_CONFIG: Record<AttendanceStatus, { label: string; dot: string; text: string }> = {
  PRESENT:  { label: 'Present',  dot: 'bg-green-500',  text: 'text-green-700'  },
  ABSENT:   { label: 'Absent',   dot: 'bg-red-500',    text: 'text-red-700'    },
  LATE:     { label: 'Late',     dot: 'bg-amber-500',  text: 'text-amber-700'  },
  HALF_DAY: { label: 'Half day', dot: 'bg-orange-400', text: 'text-orange-700' },
  LEAVE:    { label: 'Leave',    dot: 'bg-blue-400',   text: 'text-blue-700'   },
};

function gradeColor(pct: number) {
  if (pct >= 85) return 'text-emerald-600 bg-emerald-50';
  if (pct >= 70) return 'text-green-600 bg-green-50';
  if (pct >= 50) return 'text-amber-600 bg-amber-50';
  return 'text-red-600 bg-red-50';
}

// ── ExamCard: fetches one exam's result per-student ──────────────────────────

function ExamResultRow({
  tenantId, exam, sectionId, studentId,
}: {
  tenantId: string;
  exam: ExamResponse;
  sectionId: string;
  studentId: string;
}) {
  const q = useQuery({
    queryKey: ['exam-results', tenantId, exam.id, sectionId],
    queryFn:  () => academicsApi.getResults(tenantId, exam.id, sectionId),
    enabled:  !!(tenantId && exam.id && sectionId),
    staleTime: 5 * 60_000,
    retry: false,
  });

  const result: ExamResultResponse | undefined = useMemo(
    () => q.data?.find((r) => r.studentId === studentId),
    [q.data, studentId],
  );

  return (
    <tr className="border-t border-slate-100 hover:bg-slate-50 transition">
      <td className="px-4 py-3 text-sm font-medium text-slate-800">{exam.name}</td>
      <td className="px-4 py-3 text-sm text-slate-500">
        {exam.startDate ? formatDate(exam.startDate) : '—'}
      </td>
      <td className="px-4 py-3 text-center">
        {q.isLoading ? (
          <Spinner size="sm" />
        ) : result ? (
          <span className={cn('inline-block px-2 py-0.5 rounded-full text-xs font-semibold', gradeColor(result.percentage))}>
            {result.percentage.toFixed(1)}%
          </span>
        ) : (
          <span className="text-xs text-slate-400">—</span>
        )}
      </td>
      <td className="px-4 py-3 text-center text-sm">
        {result ? (result.grade ?? '—') : '—'}
      </td>
      <td className="px-4 py-3 text-center text-sm text-slate-600">
        {result ? (result.rankInSection ? `#${result.rankInSection}` : '—') : '—'}
      </td>
      <td className="px-4 py-3 text-center">
        {result ? (
          result.pass ? (
            <CheckCircle2 size={16} className="text-green-500 mx-auto" />
          ) : (
            <XCircle size={16} className="text-red-500 mx-auto" />
          )
        ) : '—'}
      </td>
    </tr>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function StudentDashboardPage() {
  const { tenantId, studentId } = useParams<{ tenantId: string; studentId: string }>();
  const router = useRouter();

  const from90 = daysBefore(90);
  const today  = todayIso();

  // ── Data fetches ──
  const profileQ = useQuery({
    queryKey: ['student-profile', tenantId, studentId],
    queryFn:  () => studentsApi.get(tenantId, studentId),
    enabled:  !!(tenantId && studentId),
  });

  const attendanceQ = useQuery({
    queryKey: ['student-attendance', tenantId, studentId, from90, today],
    queryFn:  () => attendanceApi.getStudentAttendance(tenantId, studentId, from90, today),
    enabled:  !!(tenantId && studentId),
  });

  const feesQ = useQuery({
    queryKey: ['student-fees', tenantId, studentId],
    queryFn:  () => feesApi.studentSummary(tenantId, studentId),
    enabled:  !!(tenantId && studentId),
    retry: false,
  });

  const examsQ = useQuery({
    queryKey: ['exams', tenantId],
    queryFn:  () => academicsApi.listExams(tenantId),
    enabled:  !!tenantId,
    staleTime: 5 * 60_000,
  });

  // ── Derived attendance stats ──
  const attStats = useMemo(() => {
    if (!attendanceQ.data) return null;
    const records = attendanceQ.data;
    const total   = records.length;
    const counts  = records.reduce<Record<string, number>>((acc, r) => {
      acc[r.status] = (acc[r.status] ?? 0) + 1;
      return acc;
    }, {});
    const present = (counts['PRESENT'] ?? 0) + (counts['LATE'] ?? 0) * 0.5 + (counts['HALF_DAY'] ?? 0) * 0.5;
    const pct     = total > 0 ? Math.round((present / total) * 100) : 0;
    return { total, counts, pct };
  }, [attendanceQ.data]);

  const profile   = profileQ.data;
  const student   = profile?.student;
  const enrollment = profile?.currentEnrollment;
  const sectionId  = enrollment?.sectionId ?? '';

  if (profileQ.isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (profileQ.isError || !student) {
    return <ErrorBanner error={profileQ.error} onRetry={() => profileQ.refetch()} />;
  }

  return (
    <div className="space-y-5 max-w-5xl mx-auto">
      {/* Back button */}
      <button
        onClick={() => router.back()}
        className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800 transition"
      >
        <ArrowLeft size={15} />
        Back
      </button>

      {/* ── HERO / PROFILE CARD ─────────────────────────────────────── */}
      <Card tone="gradient" padding="none">
        <div className="px-6 py-5 flex flex-col sm:flex-row sm:items-start gap-4">
          {/* Avatar */}
          <div className="w-16 h-16 rounded-2xl bg-white/20 text-white grid place-items-center shrink-0 text-2xl font-bold">
            {student.displayName.charAt(0).toUpperCase()}
          </div>
          {/* Basic info */}
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-bold text-white leading-tight">{student.displayName}</h1>
            <div className="flex flex-wrap gap-x-4 gap-y-1 mt-1.5 text-sm text-white/80">
              {enrollment && (
                <span className="flex items-center gap-1">
                  <BookOpen size={13} />
                  {enrollment.className} — Section {enrollment.sectionName}
                  {enrollment.rollNumber !== null && ` · Roll ${enrollment.rollNumber}`}
                </span>
              )}
              {student.admissionNumber && (
                <span className="flex items-center gap-1">
                  <Award size={13} />
                  Adm. {student.admissionNumber}
                </span>
              )}
              {student.dateOfBirth && (
                <span>DOB: {formatDate(student.dateOfBirth)}</span>
              )}
              {student.gender && (
                <span className="capitalize">{student.gender.toLowerCase()}</span>
              )}
              {student.bloodGroup && (
                <span>Blood: {student.bloodGroup}</span>
              )}
            </div>
          </div>
          {/* PTM badge */}
          <div className="shrink-0 hidden sm:block">
            <span className="inline-flex items-center gap-1.5 bg-white/20 text-white text-xs font-semibold px-3 py-1.5 rounded-full">
              <Users size={12} />
              PTM Dashboard
            </span>
          </div>
        </div>

        {/* Parents strip */}
        {(profile?.parents ?? []).length > 0 && (
          <div className="border-t border-white/20 px-6 py-3 flex flex-wrap gap-4">
            {profile?.parents.map((p) => (
              <div key={p.id} className="flex items-center gap-3">
                <div className="w-7 h-7 rounded-full bg-white/20 grid place-items-center">
                  <User size={12} className="text-white" />
                </div>
                <div>
                  <div className="text-xs font-medium text-white">
                    {p.name ?? 'Parent'}
                    {p.relation && (
                      <span className="ml-1 text-white/60 capitalize">({p.relation.toLowerCase()})</span>
                    )}
                    {p.primary && (
                      <span className="ml-1.5 text-[10px] bg-white/20 text-white rounded px-1 py-0.5">Primary</span>
                    )}
                  </div>
                  <div className="flex gap-3 mt-0.5">
                    {p.phone && (
                      <a href={`tel:${p.phone}`} className="flex items-center gap-1 text-[11px] text-white/70 hover:text-white transition">
                        <Phone size={10} /> {p.phone}
                      </a>
                    )}
                    {p.email && (
                      <a href={`mailto:${p.email}`} className="flex items-center gap-1 text-[11px] text-white/70 hover:text-white transition">
                        <Mail size={10} /> {p.email}
                      </a>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* ── QUICK STATS STRIP ────────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <QuickStat
          icon={CalendarCheck2}
          label="Attendance (90d)"
          value={attStats ? `${attStats.pct}%` : '—'}
          tone={attStats ? (attStats.pct >= 75 ? 'green' : attStats.pct >= 60 ? 'amber' : 'red') : 'neutral'}
          loading={attendanceQ.isLoading}
        />
        <QuickStat
          icon={TrendingUp}
          label="School days (90d)"
          value={attStats ? `${attStats.total}` : '—'}
          tone="neutral"
          loading={attendanceQ.isLoading}
        />
        <QuickStat
          icon={Wallet}
          label="Fee due"
          value={feesQ.data ? formatINR(feesQ.data.totalOutstandingPaise) : '—'}
          tone={feesQ.data ? (feesQ.data.totalOutstandingPaise > 0 ? 'red' : 'green') : 'neutral'}
          loading={feesQ.isLoading}
        />
        <QuickStat
          icon={BadgePercent}
          label="Exams taken"
          value={examsQ.data ? String(examsQ.data.length) : '—'}
          tone="neutral"
          loading={examsQ.isLoading}
        />
      </div>

      {/* ── MAIN GRID ─────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Attendance — 2/3 width */}
        <div className="lg:col-span-2 space-y-4">
          <Card padding="none">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2">
                  <CalendarCheck2 size={16} className="text-primary" />
                  Attendance — last 90 days
                </CardTitle>
                {attStats && (
                  <span className={cn(
                    'text-sm font-bold px-2.5 py-1 rounded-full',
                    attStats.pct >= 75 ? 'bg-green-100 text-green-700'
                    : attStats.pct >= 60 ? 'bg-amber-100 text-amber-700'
                    : 'bg-red-100 text-red-700',
                  )}>
                    {attStats.pct}%
                  </span>
                )}
              </div>
            </CardHeader>
            <CardBody>
              {attendanceQ.isLoading && <Spinner />}
              {attendanceQ.isError && (
                <ErrorBanner error={attendanceQ.error} onRetry={() => attendanceQ.refetch()} />
              )}
              {attStats && (
                <div className="space-y-4">
                  {/* Stat chips */}
                  <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
                    {(Object.entries(STATUS_CONFIG) as [AttendanceStatus, typeof STATUS_CONFIG[AttendanceStatus]][]).map(([status, cfg]) => (
                      <div key={status} className="rounded-lg bg-slate-50 border border-slate-100 p-2.5 text-center">
                        <div className={cn('text-lg font-bold', cfg.text)}>
                          {attStats.counts[status] ?? 0}
                        </div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{cfg.label}</div>
                      </div>
                    ))}
                  </div>

                  {/* Progress bar */}
                  <div>
                    <div className="flex justify-between text-xs text-slate-500 mb-1">
                      <span>Attendance rate</span>
                      <span>{attStats.pct}% of {attStats.total} days</span>
                    </div>
                    <div className="h-2.5 rounded-full bg-slate-100 overflow-hidden">
                      <div
                        className={cn('h-full rounded-full transition-all', attStats.pct >= 75 ? 'bg-green-500' : attStats.pct >= 60 ? 'bg-amber-500' : 'bg-red-500')}
                        style={{ width: `${attStats.pct}%` }}
                      />
                    </div>
                    {attStats.pct < 75 && (
                      <p className="text-xs text-red-600 mt-1.5 font-medium">
                        Below 75% — discuss regular attendance with parents.
                      </p>
                    )}
                  </div>

                  {/* Recent 20 records */}
                  <div>
                    <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Recent records</h4>
                    <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                      {attendanceQ.data?.slice(0, 20).map((r) => {
                        const cfg = STATUS_CONFIG[r.status];
                        return (
                          <div key={r.id} className="flex items-center justify-between text-sm">
                            <span className="text-slate-500">{formatDate(r.date)}</span>
                            <span className={cn('flex items-center gap-1.5 text-xs font-medium', cfg.text)}>
                              <span className={cn('w-2 h-2 rounded-full', cfg.dot)} />
                              {cfg.label}
                            </span>
                          </div>
                        );
                      })}
                      {!attendanceQ.data?.length && (
                        <p className="text-sm text-slate-400 text-center py-4">No attendance records in the last 90 days.</p>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </CardBody>
          </Card>

          {/* Exam Results */}
          <Card padding="none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <TrendingUp size={16} className="text-primary" />
                Exam results &amp; progress reports
              </CardTitle>
            </CardHeader>
            {examsQ.isLoading ? (
              <CardBody><Spinner /></CardBody>
            ) : (examsQ.data?.length ?? 0) === 0 ? (
              <CardBody>
                <p className="text-sm text-slate-400 text-center py-4">No exams configured yet.</p>
              </CardBody>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 text-slate-500 text-xs uppercase">
                    <tr>
                      <th className="text-left px-4 py-2.5 font-medium">Exam</th>
                      <th className="text-left px-4 py-2.5 font-medium">Date</th>
                      <th className="text-center px-4 py-2.5 font-medium">Score %</th>
                      <th className="text-center px-4 py-2.5 font-medium">Grade</th>
                      <th className="text-center px-4 py-2.5 font-medium">Rank</th>
                      <th className="text-center px-4 py-2.5 font-medium">Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    {examsQ.data?.map((exam) => (
                      <ExamResultRow
                        key={exam.id}
                        tenantId={tenantId}
                        exam={exam}
                        sectionId={sectionId}
                        studentId={studentId}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>

        {/* Right column — Fee summary + siblings */}
        <div className="space-y-4">
          {/* Fee card */}
          <Card padding="none">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Wallet size={16} className="text-primary" />
                Fee status
              </CardTitle>
            </CardHeader>
            <CardBody>
              {feesQ.isLoading && <Spinner />}
              {feesQ.isError && (
                <p className="text-sm text-slate-400 text-center py-2">Fee data unavailable</p>
              )}
              {feesQ.data && (
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-2">
                    <div className="bg-green-50 rounded-lg p-3 text-center">
                      <div className="text-base font-bold text-green-700">{formatINR(feesQ.data.totalPaidPaise)}</div>
                      <div className="text-[10px] text-green-600 mt-0.5">Total paid</div>
                    </div>
                    <div className={cn('rounded-lg p-3 text-center', feesQ.data.totalOutstandingPaise > 0 ? 'bg-red-50' : 'bg-slate-50')}>
                      <div className={cn('text-base font-bold', feesQ.data.totalOutstandingPaise > 0 ? 'text-red-700' : 'text-slate-500')}>
                        {formatINR(feesQ.data.totalOutstandingPaise)}
                      </div>
                      <div className={cn('text-[10px] mt-0.5', feesQ.data.totalOutstandingPaise > 0 ? 'text-red-600' : 'text-slate-500')}>
                        Outstanding
                      </div>
                    </div>
                  </div>

                  {/* Recent invoices */}
                  {feesQ.data.invoices.length > 0 && (
                    <div>
                      <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Invoices</h4>
                      <div className="space-y-1.5 max-h-40 overflow-y-auto">
                        {feesQ.data.invoices.slice(0, 5).map((inv) => (
                          <div key={inv.id} className="flex items-center justify-between text-xs">
                            <div>
                              <div className="font-medium text-slate-700">{inv.description ?? `Invoice ${inv.id.slice(0, 6)}`}</div>
                              <div className="text-slate-400">{formatDate(inv.dueDate)}</div>
                            </div>
                            <span className={cn('font-semibold', inv.balancePaise > 0 ? 'text-red-600' : 'text-green-600')}>
                              {formatINR(inv.balancePaise)}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Recent payments */}
                  {feesQ.data.recentPayments.length > 0 && (
                    <div>
                      <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Recent payments</h4>
                      <div className="space-y-1.5">
                        {feesQ.data.recentPayments.slice(0, 3).map((p) => (
                          <div key={p.id} className="flex items-center justify-between text-xs">
                            <div>
                              <div className="font-medium text-slate-700">{formatINR(p.amountPaise)}</div>
                              <div className="text-slate-400">{formatDate(p.paymentDate)}</div>
                            </div>
                            <span className="text-slate-500 capitalize">{p.paymentMode.toLowerCase().replace('_', ' ')}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </CardBody>
          </Card>

          {/* Siblings card */}
          {(profile?.siblings ?? []).length > 0 && (
            <Card padding="none">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Users size={16} className="text-primary" />
                  Siblings
                </CardTitle>
              </CardHeader>
              <CardBody>
                <ul className="space-y-2">
                  {profile?.siblings.map((sib) => (
                    <li key={sib.id}>
                      <a
                        href={`/tenants/${tenantId}/students/${sib.id}/dashboard`}
                        className="flex items-center gap-2 text-sm text-primary hover:underline"
                      >
                        <div className="w-7 h-7 rounded-full bg-primary/10 text-primary grid place-items-center text-xs font-semibold">
                          {sib.displayName.charAt(0)}
                        </div>
                        {sib.displayName}
                        <ChevronRight size={13} className="text-slate-300 ml-auto" />
                      </a>
                    </li>
                  ))}
                </ul>
              </CardBody>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

// ── QuickStat chip ─────────────────────────────────────────────────────────

type StatTone = 'green' | 'amber' | 'red' | 'neutral';

function QuickStat({
  icon: Icon, label, value, tone, loading,
}: {
  icon: React.ElementType;
  label: string;
  value: string;
  tone: StatTone;
  loading?: boolean;
}) {
  const colors: Record<StatTone, string> = {
    green:   'text-green-600',
    amber:   'text-amber-600',
    red:     'text-red-600',
    neutral: 'text-slate-700',
  };
  const bg: Record<StatTone, string> = {
    green:   'bg-green-50 border-green-100',
    amber:   'bg-amber-50 border-amber-100',
    red:     'bg-red-50 border-red-100',
    neutral: 'bg-white border-slate-200',
  };
  return (
    <div className={cn('rounded-xl border p-3 shadow-sm', bg[tone])}>
      <div className="flex items-center gap-1.5 text-xs text-slate-500 mb-1.5">
        <Icon size={13} className={colors[tone]} />
        <span>{label}</span>
      </div>
      {loading ? (
        <div className="h-6 flex items-center"><Spinner size="sm" /></div>
      ) : (
        <div className={cn('text-xl font-bold leading-none', colors[tone])}>{value}</div>
      )}
    </div>
  );
}
