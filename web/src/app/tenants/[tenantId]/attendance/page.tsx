'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useRouter } from 'next/navigation';
import {
  Shield, X, ChevronDown, ChevronRight,
  CheckCircle2, XCircle,
} from 'lucide-react';
import { attendanceApi } from '@/api/endpoints/attendance';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Button } from '@/components/ui/Button';
import { useHasRole, OWNER_OR_ADMIN } from '@/auth/RequireRole';
import { useAuth } from '@/auth/AuthProvider';
import { todayIso, cn } from '@/lib/utils';
import type {
  AttendanceEntry, AttendanceRecordResponse,
  AttendanceStatus, SectionResponse,
} from '@/types/domain';

// ─── Constants ─────────────────────────────────────────────────────────────
const STATUS_STYLES: Record<AttendanceStatus, string> = {
  PRESENT:  'bg-green-100 text-green-800 border-green-200',
  ABSENT:   'bg-red-100 text-red-800 border-red-200',
  LATE:     'bg-amber-100 text-amber-800 border-amber-200',
  HALF_DAY: 'bg-blue-100 text-blue-800 border-blue-200',
  LEAVE:    'bg-slate-100 text-slate-700 border-slate-200',
};
const STATUS_CYCLE: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'LEAVE'];

function daysBefore(n: number) {
  const d = new Date(); d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

// ─── Section Detail Panel ───────────────────────────────────────────────────
interface SectionPanelProps {
  tenantId: string; sectionId: string; sectionLabel: string; date: string; onClose: () => void;
}
function SectionDetailPanel({ tenantId, sectionId, sectionLabel, date, onClose }: SectionPanelProps) {
  const qc = useQueryClient();
  const [overrideEnabled, setOverrideEnabled] = useState(false);
  const [edited, setEdited] = useState<Map<string, AttendanceStatus>>(new Map());
  const [dirty, setDirty] = useState(false);

  const sectionQ = useQuery({
    queryKey: ['attendance', tenantId, sectionId, date],
    queryFn: () => attendanceApi.getSection(tenantId, sectionId, date),
    enabled: !!sectionId,
  });
  const rosterQ = useQuery({
    queryKey: ['section-roster', tenantId, sectionId],
    queryFn: () => studentsApi.bySection(tenantId, sectionId),
    staleTime: 60_000, enabled: !!sectionId,
  });

  // Seed editable map from records whenever section data loads
  useEffect(() => {
    if (!sectionQ.data) return;
    const m = new Map<string, AttendanceStatus>();
    sectionQ.data.records.forEach((r) => m.set(r.studentId, r.status));
    setEdited(m); setDirty(false);
  }, [sectionQ.data]);

  const nameMap = useMemo(() => {
    const m = new Map<string, string>();
    (rosterQ.data ?? []).forEach((s) => m.set(s.id, s.displayName));
    return m;
  }, [rosterQ.data]);

  const override = useMutation({
    mutationFn: () => {
      const entries: AttendanceEntry[] = Array.from(edited.entries()).map(([studentId, status]) => ({ studentId, status }));
      return attendanceApi.submit(tenantId, sectionId, { date, entries });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['attendance', tenantId, sectionId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-summary', tenantId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-unmarked', tenantId, date] });
      setOverrideEnabled(false); setDirty(false);
    },
  });

  function cycleStatus(sid: string) {
    setEdited((prev) => {
      const cur = prev.get(sid) ?? 'PRESENT';
      const nxt = STATUS_CYCLE[(STATUS_CYCLE.indexOf(cur) + 1) % STATUS_CYCLE.length]!;
      return new Map(prev).set(sid, nxt);
    });
    setDirty(true);
  }

  const records = sectionQ.data?.records ?? [];
  const counts = records.reduce((a, r) => ({ ...a, [r.status]: (a[r.status as AttendanceStatus] ?? 0) + 1 }), {} as Partial<Record<AttendanceStatus, number>>);

  return (
    <Card className="ring-1 ring-primary/30">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle className="text-base">{sectionLabel}</CardTitle>
            <div className="flex flex-wrap gap-3 text-xs mt-1.5">
              <span className="text-green-700 font-medium">Present: {counts.PRESENT ?? 0}</span>
              <span className="text-red-700 font-medium">Absent: {counts.ABSENT ?? 0}</span>
              <span className="text-amber-700 font-medium">Late: {counts.LATE ?? 0}</span>
              <span className="text-blue-700 font-medium">Half day: {counts.HALF_DAY ?? 0}</span>
              <span className="text-slate-600">Leave: {counts.LEAVE ?? 0}</span>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {/* Override toggle */}
            <button
              onClick={() => setOverrideEnabled((v) => !v)}
              className={cn(
                'flex items-center gap-1.5 px-2.5 py-1.5 rounded-brand border text-xs font-medium transition',
                overrideEnabled
                  ? 'bg-amber-50 border-amber-300 text-amber-700'
                  : 'bg-white border-slate-200 text-slate-500 hover:bg-slate-50',
              )}
            >
              <Shield size={12} />
              {overrideEnabled ? 'Override ON' : 'Override'}
            </button>
            <button onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded transition text-slate-400"><X size={15} /></button>
          </div>
        </div>
        {overrideEnabled && (
          <p className="mt-2 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded px-2.5 py-1.5">
            Override active — tap a student&apos;s badge to change their status, then save.
          </p>
        )}
      </CardHeader>
      <CardBody className="pt-0">
        {sectionQ.isLoading && <Spinner />}
        {sectionQ.isError && <ErrorBanner error={sectionQ.error} onRetry={() => sectionQ.refetch()} />}
        {records.length > 0 && (
          <div className="space-y-1 max-h-72 overflow-y-auto pr-1">
            {records.map((r) => {
              const name = nameMap.get(r.studentId) ?? '…';
              const cur = edited.get(r.studentId) ?? r.status;
              const changed = cur !== r.status;
              return (
                <div key={r.studentId} className={cn(
                  'flex items-center justify-between px-3 py-2 rounded-lg text-sm border',
                  changed ? 'border-amber-300 bg-amber-50/60' : 'border-slate-100 bg-white',
                )}>
                  <span className="font-medium text-slate-800">{name}</span>
                  <button
                    disabled={!overrideEnabled}
                    onClick={() => cycleStatus(r.studentId)}
                    className={cn(
                      'px-2.5 py-0.5 text-xs rounded border font-semibold transition',
                      STATUS_STYLES[cur],
                      overrideEnabled ? 'cursor-pointer hover:opacity-75' : 'cursor-default',
                    )}
                  >
                    {cur}{changed ? ' *' : ''}
                  </button>
                </div>
              );
            })}
          </div>
        )}
        {overrideEnabled && dirty && (
          <div className="mt-3 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => { setOverrideEnabled(false); setDirty(false); if (sectionQ.data) { const m = new Map<string, AttendanceStatus>(); sectionQ.data.records.forEach((r) => m.set(r.studentId, r.status)); setEdited(m); } }}>Discard</Button>
            <Button size="sm" onClick={() => override.mutate()} disabled={override.isPending}>
              {override.isPending ? 'Saving…' : 'Save override'}
            </Button>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

// ─── Main Page ─────────────────────────────────────────────────────────────
export default function AttendanceHomePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const router = useRouter();
  const [date, setDate] = useState(todayIso());

  const isAdmin = useHasRole(...OWNER_OR_ADMIN);
  const { state } = useAuth();
  const role = state.status === 'authenticated' ? state.claims.role : '';
  const isClassTeacher = role === 'CLASS_TEACHER';

  // Block roles that have no business on the attendance page
  useEffect(() => {
    if (role === 'LIBRARIAN') router.replace(`/tenants/${tenantId}/library/books`);
    if (role === 'ACCOUNTANT') router.replace(`/tenants/${tenantId}/fees/dashboard`);
  }, [role, tenantId, router]);

  if (role === 'LIBRARIAN' || role === 'ACCOUNTANT') return null;

  // ── Admin queries ──
  const summary = useQuery({
    queryKey: ['attendance-summary', tenantId, date],
    queryFn: () => attendanceApi.summary(tenantId, date),
    enabled: !!tenantId && isAdmin,
  });
  const unmarked = useQuery({
    queryKey: ['attendance-unmarked', tenantId, date],
    queryFn: () => attendanceApi.unmarked(tenantId, date),
    enabled: !!tenantId && isAdmin,
  });
  const classes = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId && isAdmin,
    staleTime: 5 * 60_000,
  });

  // ── CLASS_TEACHER query ──
  const mineSections = useQuery({
    queryKey: ['sections-mine', tenantId],
    queryFn: () => schoolApi.myAssignedSections(tenantId),
    enabled: !!tenantId && isClassTeacher,
    staleTime: 60_000,
  });

  // ── Admin state ──
  const [takenFilter, setTakenFilter] = useState('');  // classId filter for "taken" list
  const [selectedSection, setSelectedSection] = useState<{ id: string; label: string } | null>(null);

  // ── Derived: marked vs unmarked sections ──
  const unmarkedIds = useMemo(() => new Set((unmarked.data ?? []).map((u) => u.sectionId)), [unmarked.data]);

  const allFlatSections = useMemo(() => {
    const list: Array<{ id: string; label: string; className: string; classId: string }> = [];
    (classes.data ?? []).forEach((c) =>
      c.sections.forEach((s) =>
        list.push({ id: s.id, label: `${c.name} — ${s.name}`, className: c.name, classId: c.id }),
      ),
    );
    return list;
  }, [classes.data]);

  const markedSections = useMemo(() => allFlatSections.filter((s) => !unmarkedIds.has(s.id)), [allFlatSections, unmarkedIds]);
  const filteredMarked = takenFilter ? markedSections.filter((s) => s.classId === takenFilter) : markedSections;

  // Reset selection when date changes
  useEffect(() => { setSelectedSection(null); }, [date]);

  return (
    <div className="space-y-5">
      {/* ─── Header ─── */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Attendance</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {isClassTeacher ? 'Your assigned sections.' : 'School-wide attendance overview.'}
          </p>
        </div>
        <input
          type="date" value={date} max={todayIso()}
          onChange={(e) => setDate(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      {/* ════════════════════════════════════════════ ADMIN / PRINCIPAL VIEW ══ */}
      {isAdmin && (
        <>
          {summary.isError && <ErrorBanner error={summary.error} onRetry={() => summary.refetch()} />}

          {/* ─── Summary strip ─── */}
          <Card>
            <CardBody>
              {summary.isLoading ? <Spinner /> : summary.data ? (
                <div className="grid grid-cols-3 sm:grid-cols-6 gap-4">
                  <SummaryTile
                    label="Sections marked"
                    value={`${markedSections.length} / ${allFlatSections.length}`}
                    tone="text-slate-900"
                  />
                  <SummaryTile label="Total marked" value={summary.data.totalMarked} />
                  <SummaryTile label="Present" value={summary.data.present} tone="text-green-700" />
                  <SummaryTile label="Absent" value={summary.data.absent} tone="text-red-700" />
                  <SummaryTile label="Late" value={summary.data.late} tone="text-amber-700" />
                  <SummaryTile label="Leave" value={summary.data.leave} tone="text-slate-600" />
                </div>
              ) : null}
            </CardBody>
          </Card>

          {/* ─── Two main cards ─── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

            {/* Card 1: Attendance Taken */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <CheckCircle2 size={17} className="text-green-600 shrink-0" />
                    <CardTitle>Attendance taken</CardTitle>
                    <span className="text-xs font-semibold bg-green-100 text-green-700 px-2 py-0.5 rounded-full">
                      {markedSections.length}
                    </span>
                  </div>
                  {/* Class filter */}
                  {classes.data && classes.data.length > 0 && (
                    <select
                      value={takenFilter}
                      onChange={(e) => { setTakenFilter(e.target.value); setSelectedSection(null); }}
                      className="text-xs rounded border border-slate-300 px-2 py-1 bg-white"
                    >
                      <option value="">All classes</option>
                      {classes.data.map((c) => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                    </select>
                  )}
                </div>
                <CardDescription>Submitted by class teachers — click to view details</CardDescription>
              </CardHeader>
              <CardBody>
                {(unmarked.isLoading || classes.isLoading) && <Spinner />}
                {filteredMarked.length === 0 && !unmarked.isLoading && (
                  <p className="text-sm text-slate-500">
                    {markedSections.length === 0 ? 'No sections have marked attendance yet.' : 'No sections match this filter.'}
                  </p>
                )}
                <ul className="space-y-1.5">
                  {filteredMarked.map((sec) => (
                    <li key={sec.id}>
                      <button
                        onClick={() => setSelectedSection(selectedSection?.id === sec.id ? null : { id: sec.id, label: sec.label })}
                        className={cn(
                          'w-full flex items-center justify-between px-3 py-2.5 rounded-brand text-sm border transition',
                          selectedSection?.id === sec.id
                            ? 'bg-primary-soft border-primary/30 text-primary'
                            : 'bg-white border-slate-100 hover:bg-slate-50 text-slate-800',
                        )}
                      >
                        <span className="font-medium">{sec.label}</span>
                        <ChevronRight size={14} className={cn('transition-transform', selectedSection?.id === sec.id && 'rotate-90')} />
                      </button>
                      {/* Inline detail panel below clicked row */}
                      {selectedSection?.id === sec.id && (
                        <div className="mt-2 mb-1">
                          <SectionDetailPanel
                            tenantId={tenantId}
                            sectionId={sec.id}
                            sectionLabel={sec.label}
                            date={date}
                            onClose={() => setSelectedSection(null)}
                          />
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              </CardBody>
            </Card>

            {/* Card 2: Attendance Not Taken */}
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <XCircle size={17} className="text-red-500 shrink-0" />
                  <CardTitle>Not yet taken</CardTitle>
                  <span className="text-xs font-semibold bg-red-100 text-red-700 px-2 py-0.5 rounded-full">
                    {unmarked.data?.length ?? 0}
                  </span>
                </div>
                <CardDescription>Sections where class teachers haven&apos;t submitted yet</CardDescription>
              </CardHeader>
              <CardBody>
                {unmarked.isLoading && <Spinner />}
                {unmarked.data?.length === 0 && !unmarked.isLoading && (
                  <p className="text-sm text-slate-500">Every section has submitted attendance for {date}. 🎉</p>
                )}
                <ul className="space-y-1.5">
                  {(unmarked.data ?? []).map((u) => (
                    <li key={u.sectionId} className="flex items-center justify-between px-3 py-2.5 rounded-brand border border-slate-100 bg-white text-sm">
                      <div>
                        <div className="font-medium text-slate-800">{u.className} — {u.sectionName}</div>
                        <div className="text-xs text-slate-500 mt-0.5">{u.classTeacherName ?? 'No class teacher assigned'}</div>
                      </div>
                      <Link
                        href={`/tenants/${tenantId}/attendance/${u.sectionId}?date=${date}`}
                        className="text-primary text-sm font-medium hover:underline shrink-0"
                      >
                        Mark →
                      </Link>
                    </li>
                  ))}
                </ul>
              </CardBody>
            </Card>
          </div>


        </>
      )}

      {/* ════════════════════════════════════════════ CLASS TEACHER VIEW ══ */}
      {isClassTeacher && (
        <Card>
          <CardHeader><CardTitle>My sections</CardTitle></CardHeader>
          <CardBody>
            {mineSections.isLoading && <Spinner />}
            {mineSections.isError && <ErrorBanner error={mineSections.error} onRetry={() => mineSections.refetch()} />}
            {mineSections.data?.length === 0 && (
              <p className="text-sm text-slate-500">
                No sections assigned yet. Ask your admin from <strong>Settings → Classes</strong>.
              </p>
            )}
            {mineSections.data && mineSections.data.length > 0 && (
              <ul className="space-y-2 text-sm">
                {mineSections.data.map((s: SectionResponse) => (
                  <li key={s.id} className="flex justify-between items-center border-b border-slate-100 pb-2 last:border-b-0">
                    <div className="font-medium">
                      {s.className ? `${s.className} — Section ${s.name}` : `Section ${s.name}`}
                    </div>
                    <Link href={`/tenants/${tenantId}/attendance/${s.id}?date=${date}`} className="text-primary text-sm hover:underline">
                      Mark attendance →
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}

function SummaryTile({ label, value, tone }: { label: string; value: number | string; tone?: string }) {
  return (
    <div className="text-center">
      <div className={`text-xl font-bold ${tone ?? 'text-slate-700'}`}>{value}</div>
      <div className="text-[11px] text-slate-500 mt-0.5 leading-tight">{label}</div>
    </div>
  );
}
