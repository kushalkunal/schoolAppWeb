'use client';

import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Calendar, Check, X, Clock, Plane, Sun, AlertCircle, Search, CheckCircle,
} from 'lucide-react';
import { hrApi } from '@/api/endpoints/hr';
import { schoolApi } from '@/api/endpoints/school';
import { attendanceApi } from '@/api/endpoints/attendance';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Stat } from '@/components/ui/Stat';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole, useHasRole } from '@/auth/RequireRole';
import { useAuth } from '@/auth/AuthProvider';
import { cn } from '@/lib/utils';
import type {
  StaffAttendanceResponse, StaffAttendanceStatus, StaffResponse,
} from '@/types/domain';

const STATUS_ORDER: StaffAttendanceStatus[] = ['PRESENT', 'LATE', 'HALF_DAY', 'ABSENT', 'LEAVE', 'HOLIDAY'];

const STATUS_META: Record<StaffAttendanceStatus, {
  label: string;
  short: string;
  tone: 'success' | 'warning' | 'info' | 'danger' | 'neutral';
  bg: string;
  text: string;
  icon: React.ElementType;
}> = {
  PRESENT:  { label: 'Present',  short: 'P',  tone: 'success', bg: 'bg-success/10', text: 'text-success', icon: Check },
  LATE:     { label: 'Late',     short: 'L',  tone: 'warning', bg: 'bg-warning/10', text: 'text-warning', icon: Clock },
  HALF_DAY: { label: 'Half day', short: 'H',  tone: 'info',    bg: 'bg-info/10',    text: 'text-info',    icon: AlertCircle },
  ABSENT:   { label: 'Absent',   short: 'A',  tone: 'danger',  bg: 'bg-danger/10',  text: 'text-danger',  icon: X },
  LEAVE:    { label: 'On leave', short: 'Lv', tone: 'neutral', bg: 'bg-slate-100',  text: 'text-slate-600', icon: Plane },
  HOLIDAY:  { label: 'Holiday',  short: 'Ho', tone: 'neutral', bg: 'bg-slate-100',  text: 'text-slate-600', icon: Sun },
};

const todayIso = () => new Date().toISOString().slice(0, 10);

export default function StaffAttendancePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();
  const isAdmin = useHasRole(...OWNER_OR_ADMIN);
  const { state } = useAuth();
  const myStaffId = state.status === 'authenticated' ? state.claims.sub : '';

  const [date, setDate] = useState(todayIso());
  const [search, setSearch] = useState('');
  // Pending changes since last save — keyed by staffId.
  const [pending, setPending] = useState<Record<string, StaffAttendanceStatus>>({});
  // Teacher self-mark: chosen status before submitting
  const [selfStatus, setSelfStatus] = useState<StaffAttendanceStatus>('PRESENT');

  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId && isAdmin,
  });

  const dayQ = useQuery({
    queryKey: ['hr-attendance', tenantId, date],
    queryFn: () => hrApi.listAttendance(tenantId, date),
    enabled: !!tenantId && isAdmin,
    retry: false,
  });

  // Non-admin teacher: own attendance history (current month)
  const today = todayIso();
  const monthStart = today.slice(0, 7) + '-01';
  const myAttendanceQ = useQuery({
    queryKey: ['staff-attendance-me', tenantId, monthStart, today],
    queryFn: () => attendanceApi.myAttendance(tenantId, monthStart, today),
    enabled: !!tenantId && !!myStaffId && !isAdmin,
  });

  const markSelfMutation = useMutation({
    mutationFn: () => attendanceApi.markSelf(tenantId, selfStatus),
    onSuccess: () => {
      toast.success('Attendance submitted — awaiting approval');
      qc.invalidateQueries({ queryKey: ['staff-attendance-me', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not submit attendance'),
  });

  // Admin: pending teacher self-attendances needing approval
  const pendingApprovalsQ = useQuery({
    queryKey: ['staff-attendance-pending', tenantId, date],
    queryFn: () => attendanceApi.staffPendingApprovals(tenantId, date),
    enabled: !!tenantId && isAdmin,
    refetchInterval: 30_000,
  });

  const approveMutation = useMutation({
    mutationFn: ({ staffId, d }: { staffId: string; d: string }) =>
      attendanceApi.approveStaff(tenantId, staffId, d),
    onSuccess: () => {
      toast.success('Attendance approved');
      qc.invalidateQueries({ queryKey: ['staff-attendance-pending', tenantId] });
      qc.invalidateQueries({ queryKey: ['hr-attendance', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not approve'),
  });

  // Reset pending edits when the day changes or new server data arrives.
  useEffect(() => { setPending({}); }, [date, dayQ.dataUpdatedAt]);

  // Map staffId → existing server status for the day.
  const serverByStaff = useMemo(() => {
    const m = new Map<string, StaffAttendanceResponse>();
    for (const r of dayQ.data ?? []) m.set(r.staffId, r);
    return m;
  }, [dayQ.data]);

  function statusOf(staffId: string): StaffAttendanceStatus | null {
    if (pending[staffId]) return pending[staffId]!;
    return serverByStaff.get(staffId)?.status ?? null;
  }

  function cycle(staffId: string) {
    const current = statusOf(staffId);
    const idx = current ? STATUS_ORDER.indexOf(current) : -1;
    const next = STATUS_ORDER[(idx + 1) % STATUS_ORDER.length]!;
    setPending((p) => ({ ...p, [staffId]: next }));
  }

  function setExplicit(staffId: string, status: StaffAttendanceStatus) {
    setPending((p) => ({ ...p, [staffId]: status }));
  }

  const save = useMutation({
    mutationFn: () => {
      const requests = Object.entries(pending).map(([staffId, status]) => ({
        staffId, date, status,
      }));
      return hrApi.bulkMarkAttendance(tenantId, requests);
    },
    onSuccess: () => {
      toast.success(`Saved ${Object.keys(pending).length} updates`);
      setPending({});
      qc.invalidateQueries({ queryKey: ['hr-attendance', tenantId, date] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not save'),
  });

  // Bulk preset — "mark all unmarked staff as PRESENT".
  function markAllPresent() {
    const staff = staffQ.data ?? [];
    const additions: Record<string, StaffAttendanceStatus> = {};
    for (const s of staff) {
      if (!s.id) continue;
      const cur = statusOf(s.id);
      if (cur == null) additions[s.id] = 'PRESENT';
    }
    if (Object.keys(additions).length === 0) {
      toast.info('Every staff member is already marked.');
      return;
    }
    setPending((p) => ({ ...p, ...additions }));
  }

  // ---------------- Counts for the summary tiles ----------------
  const counts = useMemo(() => {
    const c: Record<StaffAttendanceStatus, number> = {
      PRESENT: 0, ABSENT: 0, HALF_DAY: 0, LEAVE: 0, HOLIDAY: 0, LATE: 0,
    };
    for (const s of staffQ.data ?? []) {
      if (!s.id) continue;
      const status = statusOf(s.id);
      if (status) c[status]++;
    }
    return c;
  }, [staffQ.data, pending, serverByStaff]);

  const totalStaff = (staffQ.data ?? []).filter((s) => s.active && s.id).length;
  const marked = Object.values(counts).reduce((a, b) => a + b, 0);
  const pendingCount = Object.keys(pending).length;

  // ---------------- Filtering ----------------
  const filteredStaff = useMemo(() => {
    const list = (staffQ.data ?? []).filter((s) => s.active && s.id);
    if (!search.trim()) return list;
    const q = search.toLowerCase();
    return list.filter((s) => s.displayName.toLowerCase().includes(q));
  }, [staffQ.data, search]);

  // ---------------- Render ----------------
  if (staffQ.isLoading && isAdmin) {
    return <Skeleton className="h-96" />;
  }

  if (dayQ.isError && hasCode(dayQ.error, 'FEATURE_DISABLED')) {
    return (
      <EmptyState
        icon={<Calendar size={28} />}
        title="Staff attendance isn't enabled for your plan"
        description="Track staff attendance alongside students, with monthly summaries feeding payroll."
      />
    );
  }

  // ---- Non-admin teacher: self-attendance view ----
  if (!isAdmin) {
    const todayRecord = myAttendanceQ.data?.find((r) => r.date === todayIso());
    return (
      <div className="space-y-5">
        <div>
          <h1 className="text-2xl font-semibold">My Attendance</h1>
          <p className="text-sm text-slate-500">Mark your attendance for today or view your history.</p>
        </div>

        {/* Mark today */}
        <Card padding="md">
          <CardHeader>
            <CardTitle>Mark today&apos;s attendance</CardTitle>
            <CardDescription>
              {todayRecord
                ? todayRecord.approved
                  ? `Approved by ${todayRecord.approvedByName ?? 'admin'}`
                  : 'Submitted — awaiting approval'
                : 'Not yet submitted for today'}
            </CardDescription>
          </CardHeader>
          <CardBody>
            <div className="flex flex-wrap items-center gap-3">
              {(['PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'LEAVE'] as StaffAttendanceStatus[]).map((s) => {
                const meta = STATUS_META[s];
                return (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setSelfStatus(s)}
                    className={cn(
                      'px-4 py-2 rounded-brand border text-sm font-medium transition',
                      selfStatus === s
                        ? `${meta.bg} ${meta.text} border-transparent`
                        : 'border-slate-200 text-slate-600 hover:border-slate-400',
                    )}
                  >
                    {meta.label}
                  </button>
                );
              })}
              <Button
                className="ml-auto"
                loading={markSelfMutation.isPending}
                onClick={() => markSelfMutation.mutate()}
                glow
              >
                <Check size={14} /> Submit
              </Button>
            </div>
          </CardBody>
        </Card>

        {/* Own attendance history */}
        <Card padding="none" className="overflow-hidden">
          <CardHeader>
            <CardTitle>This month</CardTitle>
            <CardDescription>Your submitted attendance records</CardDescription>
          </CardHeader>
          {myAttendanceQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
          {myAttendanceQ.isError && (
            <CardBody><ErrorBanner error={myAttendanceQ.error} onRetry={() => myAttendanceQ.refetch()} /></CardBody>
          )}
          {myAttendanceQ.data && myAttendanceQ.data.length === 0 && (
            <CardBody>
              <p className="text-sm text-slate-500 py-6 text-center">No attendance records this month.</p>
            </CardBody>
          )}
          {myAttendanceQ.data && myAttendanceQ.data.length > 0 && (
            <ul className="divide-y divide-slate-100">
              {myAttendanceQ.data.map((rec) => {
                const meta = STATUS_META[rec.status];
                return (
                  <li key={rec.id} className="px-5 py-3 flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <span className={cn('px-2 py-0.5 rounded text-xs font-medium', meta.bg, meta.text)}>
                        {meta.short}
                      </span>
                      <span className="text-sm text-slate-700">
                        {new Date(rec.date).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 text-xs">
                      {rec.approved ? (
                        <Badge tone="success" size="sm" dot>
                          Approved{rec.approvedByName ? ` by ${rec.approvedByName}` : ''}
                        </Badge>
                      ) : (
                        <Badge tone="warning" size="sm" dot>Pending approval</Badge>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Pending self-attendance approvals */}
      {(pendingApprovalsQ.data ?? []).length > 0 && (
        <Card padding="none" className="overflow-hidden border-amber-200 bg-amber-50/50">
          <CardHeader className="flex items-center justify-between">
            <div>
              <CardTitle className="text-amber-800">Pending staff self-attendances</CardTitle>
              <CardDescription className="text-amber-700">
                These staff members have marked their own attendance and are awaiting your approval.
              </CardDescription>
            </div>
            <Badge tone="warning">{pendingApprovalsQ.data?.length ?? 0}</Badge>
          </CardHeader>
          <ul className="divide-y divide-amber-100">
            {(pendingApprovalsQ.data ?? []).map((rec) => {
              const meta = STATUS_META[rec.status];
              return (
                <li key={rec.id} className="px-5 py-3 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <span className={cn('px-2 py-0.5 rounded text-xs font-medium', meta.bg, meta.text)}>
                      {meta.short}
                    </span>
                    <span className="text-sm font-medium text-slate-800">{rec.staffName}</span>
                    <span className="text-xs text-slate-500">
                      {new Date(rec.date).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })}
                    </span>
                    {rec.notes && (
                      <span className="text-xs text-slate-500 italic">&quot;{rec.notes}&quot;</span>
                    )}
                  </div>
                  <Button
                    size="sm"
                    variant="subtle"
                    loading={approveMutation.isPending}
                    onClick={() => approveMutation.mutate({ staffId: rec.staffId, d: rec.date })}
                  >
                    <CheckCircle size={14} /> Approve
                  </Button>
                </li>
              );
            })}
          </ul>
        </Card>
      )}

      {/* Date + actions header */}
      <Card padding="md" className="flex flex-wrap items-end gap-3">
        <label className="block flex-1 max-w-xs">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide flex items-center gap-1.5">
            <Calendar size={12} /> Date
          </span>
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            max={todayIso()}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          />
        </label>

        <div className="ml-auto flex items-center gap-2">
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button variant="subtle" size="sm" onClick={markAllPresent}>
              <Check size={14} /> Mark all unmarked Present
            </Button>
            <Button onClick={() => save.mutate()} loading={save.isPending}
                    disabled={pendingCount === 0} glow>
              Save {pendingCount > 0 && `(${pendingCount})`}
            </Button>
          </RequireRole>
        </div>
      </Card>

      {/* KPI summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
        <Stat label="Staff"    value={totalStaff} icon={<Calendar size={16} />} tone="primary" />
        <Stat label="Marked"   value={`${marked}/${totalStaff}`} icon={<Check size={16} />} tone="info" />
        <Stat label="Present"  value={counts.PRESENT}  icon={<Check size={16} />} tone="success" />
        <Stat label="Absent"   value={counts.ABSENT}   icon={<X size={16} />}     tone="danger" />
        <Stat label="Late"     value={counts.LATE}     icon={<Clock size={16} />} tone="warning" />
        <Stat label="On leave" value={counts.LEAVE}    icon={<Plane size={16} />} tone="info" />
      </div>

      {/* Staff list */}
      <Card padding="none" className="overflow-hidden">
        <CardHeader className="flex items-center justify-between gap-3">
          <div>
            <CardTitle>Staff list</CardTitle>
            <CardDescription>Click a staff row to cycle, or pick a specific status</CardDescription>
          </div>
          <label className="relative">
            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search staff"
              className="pl-8 pr-3 py-1.5 rounded-brand border border-slate-300 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition w-56"
            />
          </label>
        </CardHeader>

        {dayQ.isError && !hasCode(dayQ.error, 'FEATURE_DISABLED') && (
          <CardBody>
            <ErrorBanner error={dayQ.error} onRetry={() => dayQ.refetch()} />
          </CardBody>
        )}

        {filteredStaff.length === 0 ? (
          <CardBody>
            <p className="text-sm text-slate-500 text-center py-8">No staff match this filter.</p>
          </CardBody>
        ) : (
          <ul className="divide-y divide-slate-100">
            {filteredStaff.map((s) => (
              <StaffRow
                key={s.id}
                staff={s}
                status={statusOf(s.id!)}
                isPending={!!pending[s.id!]}
                onCycle={() => cycle(s.id!)}
                onSet={(st) => setExplicit(s.id!, st)}
              />
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

// ============================================================
// Row
// ============================================================

function StaffRow({
  staff, status, isPending, onCycle, onSet,
}: {
  staff: StaffResponse;
  status: StaffAttendanceStatus | null;
  isPending: boolean;
  onCycle: () => void;
  onSet: (s: StaffAttendanceStatus) => void;
}) {
  const meta = status ? STATUS_META[status] : null;
  return (
    <li
      className={cn(
        'flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50/60 transition',
        isPending && 'bg-primary-soft/40',
      )}
    >
      <div className="min-w-0 flex-1 flex items-center gap-3">
        <span className="w-8 h-8 rounded-full bg-brand-gradient text-primary-fg grid place-items-center text-xs font-semibold">
          {staff.displayName.charAt(0).toUpperCase()}
        </span>
        <div className="min-w-0">
          <div className="text-sm font-medium text-slate-800 truncate">{staff.displayName}</div>
          <div className="text-[11px] text-slate-500 truncate">
            {staff.role.replace(/_/g, ' ').toLowerCase()}
            {staff.phone && ` · ${staff.phone}`}
          </div>
        </div>
      </div>

      {/* Cycle button — large click target */}
      <button
        onClick={onCycle}
        className={cn(
          'inline-flex items-center justify-center gap-1.5 px-3 h-9 rounded-brand text-sm font-medium transition min-w-[120px]',
          status
            ? statusButtonClass(meta!.tone)
            : 'bg-slate-100 text-slate-500 hover:bg-slate-200',
        )}
        title="Click to cycle status"
      >
        {meta ? (
          <>
            <meta.icon size={14} />
            {meta.label}
          </>
        ) : (
          'Unmarked'
        )}
      </button>

      {/* Quick-pick chips — visible on hover only on desktop */}
      <div className="hidden md:flex items-center gap-1">
        {STATUS_ORDER.map((s) => {
          const m = STATUS_META[s];
          const active = status === s;
          return (
            <button
              key={s}
              onClick={() => onSet(s)}
              title={m.label}
              className={cn(
                'w-7 h-7 grid place-items-center rounded text-[10px] font-bold transition',
                active
                  ? statusButtonClass(m.tone, /* solid */ true)
                  : 'bg-slate-100 text-slate-400 hover:bg-slate-200',
              )}
            >
              {m.short}
            </button>
          );
        })}
      </div>
    </li>
  );
}

function statusButtonClass(tone: 'success' | 'warning' | 'info' | 'danger' | 'neutral', solid = false): string {
  const soft: Record<typeof tone, string> = {
    success: 'bg-success/15 text-success',
    warning: 'bg-warning/15 text-warning',
    info:    'bg-info/15    text-info',
    danger:  'bg-danger/15  text-danger',
    neutral: 'bg-slate-200  text-slate-700',
  };
  const filled: Record<typeof tone, string> = {
    success: 'bg-success text-white',
    warning: 'bg-warning text-white',
    info:    'bg-info    text-white',
    danger:  'bg-danger  text-white',
    neutral: 'bg-slate-500 text-white',
  };
  return solid ? filled[tone] : soft[tone];
}
