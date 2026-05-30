'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'next/navigation';
import { attendanceApi } from '@/api/endpoints/attendance';
import { studentsApi } from '@/api/endpoints/students';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Button } from '@/components/ui/Button';
import { ATTENDANCE_WRITER, useHasRole } from '@/auth/RequireRole';
import { useAuth } from '@/auth/AuthProvider';
import { todayIso } from '@/lib/utils';
import type { AttendanceEntry, AttendanceStatus, StudentResponse } from '@/types/domain';

const STATUS_CYCLE: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'LEAVE'];
const STATUS_STYLES: Record<AttendanceStatus, string> = {
  PRESENT:  'bg-green-100 text-green-800 border-green-300',
  ABSENT:   'bg-red-100 text-red-800 border-red-300',
  LATE:     'bg-amber-100 text-amber-800 border-amber-300',
  HALF_DAY: 'bg-blue-100 text-blue-800 border-blue-300',
  LEAVE:    'bg-slate-100 text-slate-700 border-slate-300',
};

export default function SectionAttendancePage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const sectionId = typeof params.sectionId === 'string' ? params.sectionId : '';
  const date = searchParams.get('date') ?? todayIso();
  const canMark = useHasRole(...ATTENDANCE_WRITER);
  const { state } = useAuth();
  const isPrincipal =
    state.status === 'authenticated' &&
    (state.claims.role === 'PRINCIPAL' || state.claims.role === 'SCHOOL_OWNER' || state.claims.role === 'ADMIN');
  const qc = useQueryClient();

  // Existing attendance records for this section + date.
  const records = useQuery({
    queryKey: ['attendance', tenantId, sectionId, date],
    queryFn: () => attendanceApi.getSection(tenantId, sectionId, date),
    enabled: !!tenantId && !!sectionId,
  });

  // Slice 36 — active section roster. Seeds the grid when no records exist yet, so a fresh
  // class can mark attendance directly from this page (fixes the "use the mobile app" dead end).
  const roster = useQuery({
    queryKey: ['section-roster', tenantId, sectionId],
    queryFn: () => studentsApi.bySection(tenantId, sectionId),
    enabled: !!tenantId && !!sectionId,
    staleTime: 60_000,
  });

  // Broader student list — fallback name lookup if roster is still loading.
  const allStudents = useQuery({
    queryKey: ['students', tenantId, { all: true }],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });

  const [statuses, setStatuses] = useState<Map<string, AttendanceStatus>>(new Map());

  // Seed local state. If records exist, mirror them. Otherwise default every roster student
  // to PRESENT so a single tap-cycle marks the exceptions.
  useEffect(() => {
    if (records.data === undefined) return;
    const m = new Map<string, AttendanceStatus>();
    records.data.records.forEach((r) => m.set(r.studentId, r.status));
    if (records.data.records.length === 0 && roster.data) {
      roster.data.forEach((s) => m.set(s.id, 'PRESENT'));
    }
    setStatuses(m);
  }, [records.data, roster.data]);

  const submit = useMutation({
    mutationFn: () => {
      const entries: AttendanceEntry[] = Array.from(statuses.entries()).map(([studentId, status]) => ({
        studentId,
        status,
      }));
      return attendanceApi.submit(tenantId, sectionId, { date, entries });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['attendance', tenantId, sectionId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-summary', tenantId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-unmarked', tenantId, date] });
    },
  });

  if (records.isLoading || allStudents.isLoading) {
    return <div className="text-slate-500 flex items-center gap-2"><Spinner /> Loading…</div>;
  }
  if (records.isError) {
    return <ErrorBanner error={records.error} onRetry={() => records.refetch()} />;
  }

  // Build a `studentId → displayName` lookup from the broad student list.
  const nameByStudentId = new Map<string, StudentResponse>();
  allStudents.data?.items.forEach((s) => nameByStudentId.set(s.id, s));

  const sectionData = records.data;
  const isLocked = sectionData?.locked ?? false;
  const recordsList = sectionData?.records ?? [];
  const rosterList = roster.data ?? [];
  // Effective write permission: can mark AND (not locked OR is principal/admin)
  const effectiveCanMark = canMark && (!isLocked || isPrincipal);
  const rowSource: { studentId: string }[] = recordsList.length > 0
    ? recordsList
    : rosterList.map((s) => ({ studentId: s.id }));
  const counts = countByStatus(statuses);
  const isFirstMark = recordsList.length === 0 && rosterList.length > 0;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <Link
            href={`/tenants/${tenantId}/attendance`}
            className="text-sm text-slate-500 hover:underline"
          >
            ← Attendance home
          </Link>
          <h1 className="text-2xl font-semibold mt-1">Section attendance</h1>
          <p className="text-sm text-slate-500">{date}</p>
        </div>
        {effectiveCanMark && submit.isSuccess && (
          <span className="text-sm text-green-700">
            Saved · {submit.data.notificationsQueued} parent alert(s) queued
          </span>
        )}
      </div>

      {/* Lock banners */}
      {isLocked && !isPrincipal && (
        <div className="rounded border border-slate-300 bg-slate-50 px-4 py-3 text-sm text-slate-700">
          🔒 Attendance locked by <strong>{sectionData?.lockedByName ?? 'class teacher'}</strong>.
          Contact the Principal to make corrections.
        </div>
      )}
      {isLocked && isPrincipal && (
        <div className="rounded border border-amber-400 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          ⚠️ This attendance is locked (submitted by {sectionData?.lockedByName ?? 'class teacher'}).
          As Principal, you can override and re-save.
        </div>
      )}

      {submit.isError && <ErrorBanner error={submit.error} />}

      <Card>
        <CardHeader className="flex justify-between items-center">
          <CardTitle>
            {rowSource.length === 0
              ? 'No students enrolled in this section yet'
              : `${rowSource.length} students · ${counts.PRESENT} present · ${counts.ABSENT} absent · ${counts.LATE} late`}
          </CardTitle>
          {effectiveCanMark && rowSource.length > 0 && (
            <Button onClick={() => submit.mutate()} disabled={submit.isPending}>
              {submit.isPending ? 'Saving…' : (isFirstMark ? 'Submit attendance' : isPrincipal && isLocked ? 'Save & Override' : 'Save changes')}
            </Button>
          )}
        </CardHeader>
        <CardBody>
          {rowSource.length === 0 ? (
            <p className="text-sm text-slate-500">
              No active students are enrolled in this section. Add students from
              <a href={`/tenants/${tenantId}/students`} className="text-primary underline mx-1">Students</a>
              and assign them to this section first.
            </p>
          ) : (
            <>
              {isFirstMark && !isLocked && (
                <div className="mb-3 text-xs text-slate-600 bg-primary-soft/40 border border-primary/20 rounded px-3 py-2">
                  First mark for {date}. Everyone defaults to <b>PRESENT</b> — tap a row to cycle to
                  Absent / Late / Half-day / Leave, then Submit.
                </div>
              )}
              <ul className="divide-y divide-slate-100">
              {rowSource.map((r) => {
                const student = nameByStudentId.get(r.studentId);
                const current = statuses.get(r.studentId) ?? 'PRESENT';
                return (
                  <li key={r.studentId} className="py-2 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-sm font-medium truncate">
                        {student?.displayName ?? r.studentId.slice(0, 8) + '…'}
                      </div>
                      {student?.admissionNumber && (
                        <div className="text-xs text-slate-500">{student.admissionNumber}</div>
                      )}
                    </div>
                    <StatusButton
                      status={current}
                      readonly={!effectiveCanMark}
                      onClick={() => {
                        if (!effectiveCanMark) return;
                        const next = STATUS_CYCLE[(STATUS_CYCLE.indexOf(current) + 1) % STATUS_CYCLE.length]!;
                        const m = new Map(statuses);
                        m.set(r.studentId, next);
                        setStatuses(m);
                      }}
                    />
                  </li>
                );
              })}
              </ul>
            </>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

function countByStatus(m: Map<string, AttendanceStatus>): Record<AttendanceStatus, number> {
  const c: Record<AttendanceStatus, number> = { PRESENT: 0, ABSENT: 0, LATE: 0, HALF_DAY: 0, LEAVE: 0 };
  m.forEach((s) => { c[s]++; });
  return c;
}

function StatusButton({ status, onClick, readonly }: {
  status: AttendanceStatus; onClick: () => void; readonly: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={readonly}
      className={`px-3 py-1 rounded border text-xs font-medium ${STATUS_STYLES[status]} ${readonly ? 'opacity-70 cursor-default' : 'hover:opacity-80'}`}
    >
      {status}
    </button>
  );
}
