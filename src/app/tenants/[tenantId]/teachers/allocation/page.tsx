'use client';

/**
 * Teacher Allocation Command Center.
 *
 * A single centralized screen for principals/admins to see — without page-hopping — every
 * class/section, the class-teacher and subject→teacher allocation matrix, per-teacher workload
 * with utilization, school-wide allocation gaps, and a live "who is teaching right now" snapshot.
 * Admin-only (teachers use their personal Schedule).
 */

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import {
  LayoutGrid, Users, AlertTriangle, GraduationCap, BookOpen, Clock, Radio, UserCog,
} from 'lucide-react';
import { teacherAllocationApi, type TeacherWorkload } from '@/api/endpoints/teacherAllocation';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Stat } from '@/components/ui/Stat';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

type Tab = 'matrix' | 'workload' | 'today';

export default function TeacherAllocationPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <CommandCenter />
    </RequireRole>
  );
}

function utilTone(pct: number): 'success' | 'warning' | 'danger' | 'neutral' {
  if (pct === 0) return 'neutral';
  if (pct > 90) return 'danger';
  if (pct >= 50) return 'success';
  return 'warning';
}

function CommandCenter() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [tab, setTab] = useState<Tab>('matrix');
  const [teacherFilter, setTeacherFilter] = useState('');

  const q = useQuery({
    queryKey: ['allocation-overview', tenantId],
    queryFn: () => teacherAllocationApi.overview(tenantId),
    enabled: !!tenantId,
    staleTime: 30_000,
  });

  const filteredTeachers = useMemo(() => {
    const list = q.data?.teachers ?? [];
    const f = teacherFilter.trim().toLowerCase();
    return f ? list.filter((t) => t.name.toLowerCase().includes(f)) : list;
  }, [q.data, teacherFilter]);

  if (q.isLoading) {
    return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading allocation overview…</div>;
  }
  if (q.isError) return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;
  if (!q.data) return null;

  const { summary, classes, now } = q.data;
  const gaps = summary.sectionsWithoutClassTeacher + summary.teachersWithoutLoad;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Teacher Allocation Command Center"
        description="One place to manage and monitor class teachers, subject allocations, timetable workload and live teaching status."
        icon={<LayoutGrid />}
      />

      {/* Live "now teaching" banner */}
      {now.inSession ? (
        <Card className="border-emerald-200 bg-emerald-50/60">
          <CardBody className="flex flex-wrap items-center gap-x-6 gap-y-2">
            <div className="flex items-center gap-2 text-emerald-700">
              <Radio size={18} className="animate-pulse" />
              <span className="font-semibold">{now.currentPeriodName}</span>
              <span className="text-sm text-emerald-600">{now.startTime?.slice(0, 5)}–{now.endTime?.slice(0, 5)}</span>
            </div>
            <span className="text-sm text-slate-600">
              {now.ongoing.length} class{now.ongoing.length !== 1 ? 'es' : ''} in session right now
            </span>
          </CardBody>
        </Card>
      ) : (
        <Card className="bg-slate-50">
          <CardBody className="flex items-center gap-2 text-slate-500 text-sm">
            <Clock size={16} /> No class is in session at the moment.
          </CardBody>
        </Card>
      )}

      {/* KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Classes" value={summary.totalClasses} icon={<GraduationCap size={18} />} tone="primary"
          hint={`${summary.totalSections} sections`} />
        <Stat label="Teachers" value={summary.totalTeachers} icon={<Users size={18} />} tone="info"
          hint={`${summary.totalSubjectAssignments} subject allocations`} />
        <Stat label="Periods / day" value={summary.periodsPerDay} icon={<BookOpen size={18} />} tone="accent"
          hint={`${summary.workingDays} working days`} />
        <Stat label="Allocation gaps" value={gaps} icon={<AlertTriangle size={18} />}
          tone={gaps > 0 ? 'danger' : 'success'}
          hint={`${summary.sectionsWithoutClassTeacher} without class teacher · ${summary.teachersWithoutLoad} idle`} />
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        {([['matrix', 'Allocation Matrix'], ['workload', 'Teacher Workload'], ['today', 'Today / Now']] as [Tab, string][])
          .map(([key, label]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`px-4 py-2 text-sm font-medium -mb-px border-b-2 transition-colors ${
                tab === key ? 'border-primary text-primary' : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              {label}
            </button>
          ))}
      </div>

      {/* MATRIX */}
      {tab === 'matrix' && (
        <div className="space-y-5">
          {classes.length === 0 && <EmptyHint text="No classes configured yet." />}
          {classes.map((c) => (
            <Card key={c.classId}>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <GraduationCap size={16} className="text-primary" /> {c.className}
                </CardTitle>
              </CardHeader>
              <CardBody className="space-y-4">
                {c.sections.map((s) => (
                  <div key={s.sectionId} className="rounded-lg border border-slate-100">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 bg-slate-50/70 px-3 py-2">
                      <span className="font-medium text-slate-700">Section {s.sectionName}</span>
                      <span className="flex items-center gap-1.5 text-sm">
                        <UserCog size={14} className="text-slate-400" />
                        {s.classTeacher ? (
                          <>Class Teacher: <span className="font-medium">{s.classTeacher.name}</span></>
                        ) : (
                          <Badge tone="danger">No class teacher</Badge>
                        )}
                      </span>
                    </div>
                    {s.subjects.length === 0 ? (
                      <div className="px-3 py-3 text-sm text-amber-600 flex items-center gap-1.5">
                        <AlertTriangle size={14} /> No subjects allocated to this section.
                      </div>
                    ) : (
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                            <th className="px-3 py-2 font-medium">Subject</th>
                            <th className="px-3 py-2 font-medium">Assigned Teacher</th>
                            <th className="px-3 py-2 font-medium">Role</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-50">
                          {s.subjects.map((sub) => (
                            <tr key={sub.subjectId} className="hover:bg-slate-50/60">
                              <td className="px-3 py-2">{sub.subjectName}</td>
                              <td className="px-3 py-2">
                                {sub.teacher ? sub.teacher.name : <Badge tone="danger">Unassigned</Badge>}
                              </td>
                              <td className="px-3 py-2">
                                <Badge tone="neutral">{prettyRole(sub.teacher?.role)}</Badge>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                ))}
              </CardBody>
            </Card>
          ))}
        </div>
      )}

      {/* WORKLOAD */}
      {tab === 'workload' && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-base">Teacher Workload</CardTitle>
              <input
                value={teacherFilter}
                onChange={(e) => setTeacherFilter(e.target.value)}
                placeholder="Filter teachers…"
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm"
              />
            </div>
          </CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            <table className="w-full text-sm min-w-[720px]">
              <thead className="border-b border-slate-100">
                <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-4 py-3 font-medium">Teacher</th>
                  <th className="px-4 py-3 font-medium">Class Teacher Of</th>
                  <th className="px-4 py-3 font-medium text-center">Subjects</th>
                  <th className="px-4 py-3 font-medium text-center">Sections</th>
                  <th className="px-4 py-3 font-medium text-center">Periods/wk</th>
                  <th className="px-4 py-3 font-medium text-center">Free</th>
                  <th className="px-4 py-3 font-medium w-44">Utilization</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filteredTeachers.map((t) => (
                  <WorkloadRow key={t.staffId} t={t} />
                ))}
                {filteredTeachers.length === 0 && (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">No teachers match.</td></tr>
                )}
              </tbody>
            </table>
          </CardBody>
        </Card>
      )}

      {/* TODAY / NOW */}
      {tab === 'today' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Radio size={16} className={now.inSession ? 'text-emerald-500' : 'text-slate-400'} />
              {now.inSession ? `Currently teaching — ${now.currentPeriodName}` : 'Currently teaching'}
            </CardTitle>
          </CardHeader>
          <CardBody>
            {!now.inSession || now.ongoing.length === 0 ? (
              <EmptyHint text="No classes are running in this period." />
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                    <th className="px-3 py-2 font-medium">Class / Section</th>
                    <th className="px-3 py-2 font-medium">Subject</th>
                    <th className="px-3 py-2 font-medium">Teacher</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {now.ongoing.map((o, i) => (
                    <tr key={i} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2 font-medium">{o.sectionLabel}</td>
                      <td className="px-3 py-2">{o.subjectName}</td>
                      <td className="px-3 py-2">{o.teacherName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}

function WorkloadRow({ t }: { t: TeacherWorkload }) {
  return (
    <tr className="hover:bg-slate-50/60">
      <td className="px-4 py-3">
        <div className="font-medium text-slate-800">{t.name}</div>
        <div className="text-xs text-slate-400">{prettyRole(t.role)}</div>
      </td>
      <td className="px-4 py-3">
        {t.classTeacherOf.length > 0
          ? <div className="flex flex-wrap gap-1">{t.classTeacherOf.map((c) => <Badge key={c} tone="primary">{c}</Badge>)}</div>
          : <span className="text-slate-300">—</span>}
      </td>
      <td className="px-4 py-3 text-center">{t.subjectCount}</td>
      <td className="px-4 py-3 text-center">{t.sectionCount}</td>
      <td className="px-4 py-3 text-center font-medium">{t.periodsPerWeek}</td>
      <td className="px-4 py-3 text-center text-slate-500">{t.freePeriods}</td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="h-2 flex-1 rounded-full bg-slate-100 overflow-hidden">
            <div
              className={`h-full rounded-full ${barColor(t.utilizationPct)}`}
              style={{ width: `${Math.min(100, t.utilizationPct)}%` }}
            />
          </div>
          <Badge tone={utilTone(t.utilizationPct)}>{t.utilizationPct}%</Badge>
        </div>
      </td>
    </tr>
  );
}

function barColor(pct: number): string {
  if (pct === 0) return 'bg-slate-300';
  if (pct > 90) return 'bg-red-500';
  if (pct >= 50) return 'bg-emerald-500';
  return 'bg-amber-400';
}

function prettyRole(role?: string): string {
  if (!role) return '—';
  return role.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}

function EmptyHint({ text }: { text: string }) {
  return <div className="py-8 text-center text-sm text-slate-400">{text}</div>;
}
