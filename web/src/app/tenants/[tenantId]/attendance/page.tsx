'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { attendanceApi } from '@/api/endpoints/attendance';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { todayIso } from '@/lib/utils';

export default function AttendanceHomePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [date, setDate] = useState(todayIso());

  const summary = useQuery({
    queryKey: ['attendance-summary', tenantId, date],
    queryFn: () => attendanceApi.summary(tenantId, date),
    enabled: !!tenantId,
  });
  const unmarked = useQuery({
    queryKey: ['attendance-unmarked', tenantId, date],
    queryFn: () => attendanceApi.unmarked(tenantId, date),
    enabled: !!tenantId,
  });
  const classes = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Attendance</h1>
          <p className="text-sm text-slate-500">School-wide view for the selected day.</p>
        </div>
        <input
          type="date"
          value={date}
          max={todayIso()}
          onChange={(e) => setDate(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      {summary.isError && <ErrorBanner error={summary.error} onRetry={() => summary.refetch()} />}

      <Card>
        <CardHeader><CardTitle>Today&apos;s breakdown</CardTitle></CardHeader>
        <CardBody>
          {summary.isLoading ? (
            <Spinner />
          ) : summary.data ? (
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 text-sm">
              <Tile label="Marked" value={summary.data.totalMarked} />
              <Tile label="Present" value={summary.data.present} colour="text-green-700" />
              <Tile label="Absent" value={summary.data.absent} colour="text-red-700" />
              <Tile label="Late" value={summary.data.late} colour="text-amber-700" />
              <Tile label="Leave" value={summary.data.leave} />
            </div>
          ) : null}
        </CardBody>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader><CardTitle>Unmarked sections</CardTitle></CardHeader>
          <CardBody>
            {unmarked.isLoading && <Spinner />}
            {unmarked.data && unmarked.data.length === 0 && (
              <p className="text-sm text-slate-500">Every section has marked attendance for {date}. 🎉</p>
            )}
            {unmarked.data && unmarked.data.length > 0 && (
              <ul className="space-y-2 text-sm">
                {unmarked.data.map((u) => (
                  <li key={u.sectionId} className="flex justify-between items-center border-b border-slate-100 pb-2 last:border-b-0">
                    <div>
                      <div className="font-medium">{u.className} · {u.sectionName}</div>
                      <div className="text-xs text-slate-500">{u.classTeacherName ?? 'no class teacher set'}</div>
                    </div>
                    <Link
                      href={`/tenants/${tenantId}/attendance/${u.sectionId}?date=${date}`}
                      className="text-primary text-sm hover:underline"
                    >
                      Mark →
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader><CardTitle>All sections</CardTitle></CardHeader>
          <CardBody>
            {classes.isLoading && <Spinner />}
            {classes.data && (
              <div className="space-y-3 text-sm">
                {classes.data.map((c) => (
                  <div key={c.id}>
                    <div className="font-medium text-slate-700">{c.name}</div>
                    <div className="flex flex-wrap gap-2 mt-1">
                      {c.sections.map((s) => (
                        <Link
                          key={s.id}
                          href={`/tenants/${tenantId}/attendance/${s.id}?date=${date}`}
                          className="px-2 py-1 rounded border border-slate-200 hover:bg-slate-50 text-xs"
                        >
                          {s.name}
                        </Link>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}

function Tile({ label, value, colour }: { label: string; value: number; colour?: string }) {
  return (
    <div>
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className={`text-2xl font-semibold ${colour ?? 'text-slate-900'}`}>{value}</div>
    </div>
  );
}
