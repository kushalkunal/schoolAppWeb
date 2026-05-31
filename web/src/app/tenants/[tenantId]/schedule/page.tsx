'use client';

import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { CalendarClock, Replace } from 'lucide-react';
import { timetableApi } from '@/api/endpoints/timetable';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { hrApi } from '@/api/endpoints/hr';
import { useAuth } from '@/auth/AuthProvider';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { EmptyState } from '@/components/ui/EmptyState';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';
import type {
  AssignSubstitutionRequest,
  PeriodResponse,
  StaffAttendanceResponse,
  StaffResponse,
  SubjectResponse,
  TimetableEntryResponse,
} from '@/types/domain';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const DAY_ISO = [1, 2, 3, 4, 5, 6];

const ADMIN_ROLES = new Set(['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN']);

/** Today as YYYY-MM-DD */
function todayStr() {
  return new Date().toISOString().slice(0, 10);
}

/** Parse "HH:MM" → total minutes since midnight */
function timeToMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

/** Convert JS getDay() (0=Sun…6=Sat) to ISO weekday (1=Mon…7=Sun) */
function jsToIsoDay(jsDay: number): number {
  return jsDay === 0 ? 7 : jsDay;
}

// ---------------------------------------------------------------------------
// Assign-Substitute dialog
// ---------------------------------------------------------------------------
/** Staff whose status is PRESENT / LATE / HALF_DAY (self-marked, any approval state) */
const PRESENT_STATUSES = new Set(['PRESENT', 'LATE', 'HALF_DAY']);

interface AssignSubDialogProps {
  tenantId: string;
  periods: PeriodResponse[];
  staff: StaffResponse[];
  /** Staff who are absent today — derived from self-attendance records */
  absentStaff: StaffResponse[];
  classNameMap: Map<string, string>;
  sectionOptions: Array<{ id: string; label: string }>;
  onClose: () => void;
}

function AssignSubDialog({ tenantId, periods, staff, absentStaff, sectionOptions, onClose }: AssignSubDialogProps) {
  const qc = useQueryClient();
  const toast = useToast();

  const teachingPeriods = periods.filter((p) => !p.breakSlot).sort((a, b) => a.sortOrder - b.sortOrder);

  const [form, setForm] = useState<Partial<AssignSubstitutionRequest>>({ date: todayStr() });

  // Availability-aware candidates for the chosen date + period (+ section). Tells us who is
  // absent today, who is free at this exact slot, and who is already busy teaching it.
  const candidatesQ = useQuery({
    queryKey: ['sub-candidates', tenantId, form.date, form.periodId, form.sectionId],
    queryFn: () => timetableApi.getSubstituteCandidates(tenantId, {
      periodId: form.periodId!,
      date: form.date,
      sectionId: form.sectionId,
    }),
    enabled: !!form.periodId && !!form.date,
  });
  const candidates = candidatesQ.data;

  const mutation = useMutation({
    mutationFn: (req: AssignSubstitutionRequest) => timetableApi.assignSubstitution(tenantId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['today-me', tenantId] });
      qc.invalidateQueries({ queryKey: ['substitutions', tenantId] });
      toast.success('Substitute assigned successfully');
      onClose();
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : 'Failed to assign substitute';
      toast.error(msg);
    },
  });

  const valid =
    !!form.sectionId && !!form.periodId && !!form.substituteTeacherId && !!form.date;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    mutation.mutate(form as AssignSubstitutionRequest);
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* Date */}
      <div className="space-y-1">
        <label className="text-sm font-medium text-slate-700">Date</label>
        <Input
          type="date"
          value={form.date ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
          required
        />
      </div>

      {/* Section */}
      <div className="space-y-1">
        <label className="text-sm font-medium text-slate-700">Class / Section</label>
        <select
          className="w-full rounded-brand border border-slate-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          value={form.sectionId ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, sectionId: e.target.value }))}
          required
        >
          <option value="">Select a section</option>
          {sectionOptions.map((s) => (
            <option key={s.id} value={s.id}>{s.label}</option>
          ))}
        </select>
      </div>

      {/* Period */}
      <div className="space-y-1">
        <label className="text-sm font-medium text-slate-700">Period</label>
        <select
          className="w-full rounded-brand border border-slate-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          value={form.periodId ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, periodId: e.target.value }))}
          required
        >
          <option value="">Select a period</option>
          {teachingPeriods.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.startTime.slice(0, 5)}–{p.endTime.slice(0, 5)})
            </option>
          ))}
        </select>
      </div>

      {/* Absent teacher — only shows teachers who haven't marked present today */}
      <div className="space-y-1">
        <label className="text-sm font-medium text-slate-700">Absent teacher (optional)</label>
        <select
          className="w-full rounded-brand border border-slate-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          value={form.absentTeacherId ?? ''}
          onChange={(e) =>
            setForm((f) => ({ ...f, absentTeacherId: e.target.value || undefined }))
          }
        >
          <option value="">— Not specified —</option>
          {/* Prefer the server's "absent today" list; fall back to the client heuristic, then all staff. */}
          {candidates && candidates.absent.length > 0
            ? candidates.absent.map((c) => (
                <option key={c.staffId} value={c.staffId}>{c.name}</option>
              ))
            : absentStaff.length > 0
              ? absentStaff.map((s) => (
                  <option key={s.id} value={s.id}>{s.displayName}</option>
                ))
              : staff.map((s) => (
                  <option key={s.id} value={s.id}>{s.displayName}</option>
                ))
          }
        </select>
        {candidates && candidates.absent.length > 0 && (
          <p className="text-xs text-amber-600">
            {candidates.absent.length} teacher{candidates.absent.length !== 1 ? 's' : ''} marked absent / on leave today.
          </p>
        )}
      </div>

      {/* Substitute teacher — availability-aware once a date + period are chosen */}
      <div className="space-y-1">
        <label className="text-sm font-medium text-slate-700">Substitute teacher</label>
        <select
          className="w-full rounded-brand border border-slate-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          value={form.substituteTeacherId ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, substituteTeacherId: e.target.value }))}
          required
        >
          <option value="">Select substitute teacher</option>
          {candidates ? (
            <>
              <optgroup label={`Free this period (${candidates.available.length})`}>
                {candidates.available.map((c) => (
                  <option key={c.staffId} value={c.staffId}>{c.name}</option>
                ))}
              </optgroup>
              {candidates.busy.length > 0 && (
                <optgroup label="Busy this period">
                  {candidates.busy.map((c) => (
                    <option key={c.staffId} value={c.staffId} disabled>
                      {c.name} — {c.note}
                    </option>
                  ))}
                </optgroup>
              )}
            </>
          ) : (
            staff.map((s) => (
              <option key={s.id} value={s.id}>{s.displayName}</option>
            ))
          )}
        </select>
        {!form.periodId && (
          <p className="text-xs text-slate-400">Pick a date and period to see which teachers are free.</p>
        )}
        {candidates && candidates.available.length === 0 && (
          <p className="text-xs text-red-500">No teacher is free this period — everyone is teaching or absent.</p>
        )}
      </div>

      {/* Reason */}
      <div className="space-y-1">
        <label className="text-sm font-medium text-slate-700">Reason (optional)</label>
        <Input
          placeholder="e.g. Sick leave, training…"
          value={form.reason ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))}
        />
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
        <Button type="submit" disabled={!valid || mutation.isPending}>
          {mutation.isPending ? 'Saving…' : 'Assign substitute'}
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------
export default function SchedulePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const { state } = useAuth();
  const staffId = state.status === 'authenticated' ? state.claims.sub : '';
  const isAdmin = state.status === 'authenticated' && ADMIN_ROLES.has(state.claims.role);
  const todayIsoDay = jsToIsoDay(new Date().getDay());
  const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();

  const [subDialogOpen, setSubDialogOpen] = useState(false);

  const timetableQ = useQuery<TimetableEntryResponse[]>({
    queryKey: ['teacher-timetable', tenantId, staffId],
    queryFn: () => timetableApi.getTeacherTimetable(tenantId, staffId),
    enabled: !!tenantId && !!staffId,
  });

  // Today's classes including any substitution slots assigned to this teacher
  const todayQ = useQuery<TimetableEntryResponse[]>({
    queryKey: ['today-me', tenantId],
    queryFn: () => timetableApi.getTodayForMe(tenantId),
    enabled: !!tenantId,
  });

  const periodsQ = useQuery<PeriodResponse[]>({
    queryKey: ['periods', tenantId],
    queryFn: () => timetableApi.listPeriods(tenantId),
    enabled: !!tenantId,
  });

  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  const staffQ = useQuery<StaffResponse[]>({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId && isAdmin,
  });

  // Fetch today's self-attendance records so we can identify absent teachers
  const attendanceQ = useQuery<StaffAttendanceResponse[]>({
    queryKey: ['staff-attendance-today', tenantId],
    queryFn: () => hrApi.listAttendance(tenantId, todayStr()),
    enabled: !!tenantId && isAdmin,
    staleTime: 60_000,
  });

  const subjectsQ = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  const classNameMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const cls of classesQ.data ?? []) {
      for (const sec of cls.sections) {
        map.set(sec.id, `${cls.name} – ${sec.name}`);
      }
    }
    return map;
  }, [classesQ.data]);

  const sectionOptions = useMemo(() => {
    const opts: Array<{ id: string; label: string }> = [];
    for (const cls of classesQ.data ?? []) {
      for (const sec of cls.sections) {
        opts.push({ id: sec.id, label: `${cls.name} — ${sec.name}` });
      }
    }
    return opts;
  }, [classesQ.data]);

  const subjectMap = useMemo(
    () => new Map<string, string>((subjectsQ.data ?? []).map((s: SubjectResponse) => [s.id, s.name])),
    [subjectsQ.data]
  );

  const periodMap = useMemo(
    () => new Map<string, PeriodResponse>((periodsQ.data ?? []).map((p) => [p.id, p])),
    [periodsQ.data]
  );

  const teachingPeriods = useMemo(
    () => (periodsQ.data ?? []).filter((p) => !p.breakSlot).sort((a, b) => a.sortOrder - b.sortOrder),
    [periodsQ.data]
  );

  // Derive absent staff: teachers who have NOT marked themselves as present/late/half_day today.
  // A teacher who marked attendance as PRESENT (even pending approval) is considered present.
  const absentStaff = useMemo<StaffResponse[]>(() => {
    const allStaff = staffQ.data ?? [];
    const attendance = attendanceQ.data ?? [];
    // If attendance hasn't loaded yet, fall back to showing all staff
    if (!attendanceQ.isFetched) return allStaff;
    // Build set of staff IDs who are clearly present today
    const presentIds = new Set(
      attendance
        .filter((a) => PRESENT_STATUSES.has(a.status))
        .map((a) => a.staffId),
    );
    return allStaff.filter((s) => !presentIds.has(s.id));
  }, [staffQ.data, attendanceQ.data, attendanceQ.isFetched]);

  const isLoading = timetableQ.isLoading || periodsQ.isLoading || classesQ.isLoading;
  const isError = timetableQ.isError || periodsQ.isError;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError) {
    return <ErrorBanner error={timetableQ.error ?? periodsQ.error} onRetry={() => timetableQ.refetch()} />;
  }

  const entries = timetableQ.data ?? [];
  const todayEntries = todayQ.data ?? [];

  // Build lookup: dayOfWeek → periodId → entry
  const entryMap = new Map<number, Map<string, TimetableEntryResponse>>();
  for (const entry of entries) {
    if (!entryMap.has(entry.dayOfWeek)) entryMap.set(entry.dayOfWeek, new Map());
    entryMap.get(entry.dayOfWeek)!.set(entry.periodId, entry);
  }

  const hasAnyEntry = entries.length > 0 || todayEntries.length > 0;

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">My Weekly Schedule</h1>
          <p className="text-sm text-slate-500 mt-1">Your timetable across all assigned sections</p>
        </div>
        {isAdmin && (
          <Button onClick={() => setSubDialogOpen(true)} className="shrink-0 gap-2">
            <Replace size={16} />
            Assign substitute
          </Button>
        )}
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* TODAY'S CLASSES (includes substitution slots)                       */}
      {/* ------------------------------------------------------------------ */}
      {todayEntries.length > 0 && (
        <Card className="p-0 overflow-hidden ring-2 ring-primary/30">
          <CardHeader className="py-3 bg-primary-soft">
            <div className="flex items-center gap-2">
              <CardTitle className="text-base">Today&apos;s Classes</CardTitle>
              <Badge tone="primary" size="sm">Today</Badge>
            </div>
            <CardDescription>
              {todayEntries.length} period{todayEntries.length !== 1 ? 's' : ''} scheduled
            </CardDescription>
          </CardHeader>
          <CardBody className="py-2 px-0">
            <div className="divide-y divide-slate-100">
              {todayEntries
                .slice()
                .sort((a, b) => {
                  const pa = periodMap.get(a.periodId);
                  const pb = periodMap.get(b.periodId);
                  return (pa?.sortOrder ?? 0) - (pb?.sortOrder ?? 0);
                })
                .map((entry) => {
                  const period = periodMap.get(entry.periodId);
                  const sectionName = classNameMap.get(entry.sectionId) ?? 'Unknown class';
                  const subjectName = entry.subjectId ? (subjectMap.get(entry.subjectId) ?? '—') : '—';
                  const isSub = entry.note?.startsWith('[Substitution]') ?? false;

                  const isCurrent = period
                    ? nowMinutes >= timeToMinutes(period.startTime) &&
                      nowMinutes < timeToMinutes(period.endTime)
                    : false;

                  return (
                    <div
                      key={`${entry.periodId}-${entry.sectionId}`}
                      className={`flex items-center gap-4 px-4 py-3 text-sm ${isCurrent ? 'bg-primary-soft' : ''}`}
                    >
                      {/* Time */}
                      <div className="w-28 shrink-0 text-xs text-slate-500 font-mono">
                        {period
                          ? `${period.startTime.slice(0, 5)}–${period.endTime.slice(0, 5)}`
                          : '—'}
                      </div>
                      {/* Period name */}
                      <div className="w-20 shrink-0 text-xs font-medium text-slate-600">
                        {period?.name ?? '—'}
                      </div>
                      {/* Section */}
                      <div className="flex-1 font-semibold text-slate-800">{sectionName}</div>
                      {/* Subject */}
                      <div className="text-slate-500">{isSub ? 'Covering class' : subjectName}</div>
                      {/* Badges */}
                      <div className="flex items-center gap-1.5">
                        {isSub && (
                          <Badge tone="warning" size="sm">SUB</Badge>
                        )}
                        {isCurrent && <Badge tone="primary" size="sm">NOW</Badge>}
                      </div>
                    </div>
                  );
                })}
            </div>
          </CardBody>
        </Card>
      )}

      {!hasAnyEntry && (
        <EmptyState
          icon={<CalendarClock size={28} />}
          title="No timetable assigned"
          description="Your admin hasn't set up your weekly timetable yet."
        />
      )}

      {/* ------------------------------------------------------------------ */}
      {/* FULL WEEKLY GRID                                                    */}
      {/* ------------------------------------------------------------------ */}
      {entries.length > 0 && teachingPeriods.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-base font-semibold text-slate-700">Weekly schedule</h2>
          {DAYS.map((dayLabel, idx) => {
            const isoDay = DAY_ISO[idx]!;
            const dayEntries = entryMap.get(isoDay);
            if (!dayEntries || dayEntries.size === 0) return null;
            const isToday = isoDay === todayIsoDay;

            return (
              <Card key={isoDay} className={`p-0 overflow-hidden ${isToday ? 'ring-2 ring-primary/20' : ''}`}>
                <CardHeader className={`py-3 ${isToday ? 'bg-primary-soft' : ''}`}>
                  <div className="flex items-center gap-2">
                    <CardTitle className="text-base">{dayLabel}</CardTitle>
                    {isToday && <Badge tone="primary" size="sm">Today</Badge>}
                  </div>
                  <CardDescription>{dayEntries.size} period{dayEntries.size !== 1 ? 's' : ''}</CardDescription>
                </CardHeader>
                <CardBody className="py-2 px-0">
                  <div className="divide-y divide-slate-100">
                    {teachingPeriods
                      .filter((p) => dayEntries.has(p.id))
                      .map((period) => {
                        const entry = dayEntries.get(period.id)!;
                        const sectionName = classNameMap.get(entry.sectionId) ?? entry.sectionId;
                        const subjectName = entry.subjectId ? (subjectMap.get(entry.subjectId) ?? '—') : '—';
                        const pData = periodMap.get(period.id);

                        const isCurrent = isToday && pData
                          ? nowMinutes >= timeToMinutes(pData.startTime) &&
                            nowMinutes < timeToMinutes(pData.endTime)
                          : false;

                        return (
                          <div
                            key={period.id}
                            className={`flex items-center gap-4 px-4 py-2.5 text-sm ${isCurrent ? 'bg-primary-soft' : ''}`}
                          >
                            <div className="w-24 shrink-0 text-xs text-slate-500 font-mono">
                              {period.startTime.slice(0, 5)}–{period.endTime.slice(0, 5)}
                            </div>
                            <div className="w-20 shrink-0 text-xs font-medium text-slate-600">
                              {period.name}
                            </div>
                            <div className="flex-1 font-semibold text-slate-800">{sectionName}</div>
                            <div className="text-slate-500">{subjectName}</div>
                            {isCurrent && <Badge tone="primary" size="sm">NOW</Badge>}
                          </div>
                        );
                      })}
                  </div>
                </CardBody>
              </Card>
            );
          })}
        </div>
      )}

      {/* ------------------------------------------------------------------ */}
      {/* ASSIGN SUBSTITUTE DIALOG (admin only)                              */}
      {/* ------------------------------------------------------------------ */}
      {isAdmin && (
        <Modal
          open={subDialogOpen}
          onClose={() => setSubDialogOpen(false)}
          title="Assign substitute teacher"
        >
          <AssignSubDialog
            tenantId={tenantId}
            periods={periodsQ.data ?? []}
            staff={staffQ.data ?? []}
            absentStaff={absentStaff}
            classNameMap={classNameMap}
            sectionOptions={sectionOptions}
            onClose={() => setSubDialogOpen(false)}
          />
        </Modal>
      )}
    </div>
  );
}
