'use client';

/**
 * Live Teaching Monitor — the school's real-time operational control room.
 *
 * Auto-refreshes; derives from the timetable + clock + staff attendance: who is teaching right
 * now, who is free this period, what runs next period, and who is on leave today.
 */

import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Radio, Clock, UserCheck, Coffee, CalendarClock, Plane, RefreshCw } from 'lucide-react';
import { teacherAllocationApi } from '@/api/endpoints/teacherAllocation';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Stat } from '@/components/ui/Stat';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';

const hhmm = (t: string | null) => (t ? t.slice(0, 5) : '—');

export default function LiveMonitorPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  const q = useQuery({
    queryKey: ['live-monitor', tenantId],
    queryFn: () => teacherAllocationApi.liveMonitor(tenantId),
    enabled: !!tenantId,
    refetchInterval: 60_000,      // live: re-poll every minute
    refetchOnWindowFocus: true,
  });

  if (q.isLoading) {
    return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading live status…</div>;
  }
  if (q.isError) return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;
  if (!q.data) return null;

  const m = q.data;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Live Teaching Monitor"
        description="Real-time view of who is teaching, who is free, what's next, and who is on leave."
        icon={<Radio />}
      />

      {/* Current / next period banner */}
      <Card className={m.currentPeriod ? 'border-emerald-200 bg-emerald-50/60' : 'bg-slate-50'}>
        <CardBody className="flex flex-wrap items-center gap-x-6 gap-y-2">
          {m.currentPeriod ? (
            <div className="flex items-center gap-2 text-emerald-700">
              <Radio size={18} className="animate-pulse" />
              <span className="font-semibold">In session: {m.currentPeriod.name}</span>
              <span className="text-sm text-emerald-600">
                {hhmm(m.currentPeriod.startTime)}–{hhmm(m.currentPeriod.endTime)}
              </span>
            </div>
          ) : (
            <div className="flex items-center gap-2 text-slate-500">
              <Clock size={16} /> No class in session right now.
            </div>
          )}
          {m.nextPeriod && (
            <div className="flex items-center gap-2 text-sm text-slate-600">
              <CalendarClock size={15} className="text-slate-400" />
              Next: <span className="font-medium">{m.nextPeriod.name}</span> at {hhmm(m.nextPeriod.startTime)}
            </div>
          )}
          <button
            onClick={() => q.refetch()}
            className="ml-auto flex items-center gap-1 text-xs text-slate-400 hover:text-slate-700"
          >
            <RefreshCw size={13} className={q.isFetching ? 'animate-spin' : ''} /> Refresh
          </button>
        </CardBody>
      </Card>

      {/* KPI strip */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Total Teachers" value={m.counts.totalTeachers} icon={<UserCheck size={18} />} tone="primary" />
        <Stat label="Teaching Now" value={m.counts.teachingNow} icon={<Radio size={18} />} tone="success" />
        <Stat label="Free Now" value={m.counts.freeNow} icon={<Coffee size={18} />} tone="info" />
        <Stat label="On Leave Today" value={m.counts.onLeave} icon={<Plane size={18} />}
          tone={m.counts.onLeave > 0 ? 'warning' : 'success'} />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        {/* Currently Teaching */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Radio size={16} className="text-emerald-500" /> Currently Teaching
            </CardTitle>
          </CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            {m.teachingNow.length === 0 ? (
              <Empty text="No classes are running this period." />
            ) : (
              <table className="w-full text-sm">
                <Head cols={['Teacher', 'Class', 'Subject', 'Period', '']} />
                <tbody className="divide-y divide-slate-50">
                  {m.teachingNow.map((c, i) => (
                    <tr key={i} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2 font-medium">{c.teacherName}</td>
                      <td className="px-3 py-2">{c.sectionLabel}</td>
                      <td className="px-3 py-2">{c.subjectName}</td>
                      <td className="px-3 py-2">{c.periodName}</td>
                      <td className="px-3 py-2"><Badge tone="success">Teaching</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>

        {/* Currently Free */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Coffee size={16} className="text-sky-500" /> Currently Free Teachers
            </CardTitle>
          </CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            {m.freeNow.length === 0 ? (
              <Empty text="No free teachers right now." />
            ) : (
              <table className="w-full text-sm">
                <Head cols={['Teacher', 'Role', 'Status']} />
                <tbody className="divide-y divide-slate-50">
                  {m.freeNow.map((t) => (
                    <tr key={t.staffId} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2 font-medium">{t.name}</td>
                      <td className="px-3 py-2 text-slate-500">{prettyRole(t.role)}</td>
                      <td className="px-3 py-2"><Badge tone="info">Free</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>

        {/* Upcoming */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <CalendarClock size={16} className="text-violet-500" /> Upcoming Classes
              {m.nextPeriod && <span className="text-xs font-normal text-slate-400">({m.nextPeriod.name})</span>}
            </CardTitle>
          </CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            {m.upcoming.length === 0 ? (
              <Empty text="No classes scheduled for the next period." />
            ) : (
              <table className="w-full text-sm">
                <Head cols={['Teacher', 'Next Class', 'Subject', 'Time']} />
                <tbody className="divide-y divide-slate-50">
                  {m.upcoming.map((u, i) => (
                    <tr key={i} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2 font-medium">{u.teacherName}</td>
                      <td className="px-3 py-2">{u.sectionLabel}</td>
                      <td className="px-3 py-2">{u.subjectName}</td>
                      <td className="px-3 py-2">{hhmm(u.time)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>

        {/* On Leave */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Plane size={16} className="text-amber-500" /> Teachers On Leave
            </CardTitle>
          </CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            {m.onLeave.length === 0 ? (
              <Empty text="No teachers on leave today." />
            ) : (
              <table className="w-full text-sm">
                <Head cols={['Teacher', 'Leave Type']} />
                <tbody className="divide-y divide-slate-50">
                  {m.onLeave.map((l) => (
                    <tr key={l.staffId} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2 font-medium">{l.name}</td>
                      <td className="px-3 py-2"><Badge tone="warning">{prettyRole(l.leaveType)}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}

function Head({ cols }: { cols: string[] }) {
  return (
    <thead className="border-b border-slate-100">
      <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
        {cols.map((c, i) => <th key={i} className="px-3 py-2 font-medium">{c}</th>)}
      </tr>
    </thead>
  );
}

function Empty({ text }: { text: string }) {
  return <div className="py-8 text-center text-sm text-slate-400">{text}</div>;
}

function prettyRole(role: string): string {
  return role.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}
