'use client';

/**
 * Smart Substitute Replacement.
 *
 * Detects absent teachers (attendance + approved leave), extracts their affected periods, ranks
 * free substitutes period-wise (same-subject → knows the class → lowest workload), highlights the
 * recommended pick, and assigns the best substitute for every period in one click.
 */

import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { UserX, Wand2, AlertTriangle, CheckCircle2, Radio } from 'lucide-react';
import { substitutionApi, type PeriodPlan } from '@/api/endpoints/substitution';
import { timetableApi } from '@/api/endpoints/timetable';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Stat } from '@/components/ui/Stat';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';

const hhmm = (t: string | null) => (t ? t.slice(0, 5) : '');

export default function SmartReplacementPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();
  const [teacherId, setTeacherId] = useState('');
  const [picks, setPicks] = useState<Record<string, string>>({});   // periodId → chosen staffId

  const dashboardQ = useQuery({ queryKey: ['sub-dashboard', tenantId], queryFn: () => substitutionApi.dashboard(tenantId), enabled: !!tenantId });
  const absentQ = useQuery({ queryKey: ['sub-absent', tenantId], queryFn: () => substitutionApi.absentToday(tenantId), enabled: !!tenantId });
  const planQ = useQuery({
    queryKey: ['sub-plan', tenantId, teacherId],
    queryFn: () => substitutionApi.plan(tenantId, teacherId),
    enabled: !!tenantId && !!teacherId,
  });

  // Seed per-row picks from the recommended substitute whenever a plan loads.
  useEffect(() => {
    if (planQ.data) {
      const seed: Record<string, string> = {};
      planQ.data.periods.forEach((p) => { if (p.recommendedStaffId) seed[p.periodId] = p.recommendedStaffId; });
      setPicks(seed);
    }
  }, [planQ.data]);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['sub-plan', tenantId, teacherId] });
    qc.invalidateQueries({ queryKey: ['sub-dashboard', tenantId] });
  };

  const autoAssign = useMutation({
    mutationFn: () => substitutionApi.autoAssign(tenantId, teacherId),
    onSuccess: (r) => { toast.success(`Auto-assigned ${r.assigned} period(s); skipped ${r.skipped}`); refresh(); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Auto-assign failed'),
  });

  const assignOne = useMutation({
    mutationFn: (p: PeriodPlan) => timetableApi.assignSubstitution(tenantId, {
      sectionId: p.sectionId, periodId: p.periodId,
      date: new Date().toISOString().slice(0, 10),
      absentTeacherId: teacherId, substituteTeacherId: picks[p.periodId]!, reason: 'Substitute (manual)',
    }),
    onSuccess: () => { toast.success('Substitute assigned'); refresh(); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Could not assign'),
  });

  const d = dashboardQ.data;
  const plan = planQ.data;
  const uncovered = useMemo(() => plan?.periods.filter((p) => !p.alreadyCovered).length ?? 0, [plan]);

  return (
    <div className="space-y-6">
      <PageHeader title="Smart Substitute Replacement" description="Pick an absent teacher — the system suggests the best free substitute for each affected period." icon={<Wand2 />} />

      {/* Dashboard strip */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Absent Today" value={d?.absentTeachers ?? '—'} icon={<UserX size={18} />} tone={(d?.absentTeachers ?? 0) > 0 ? 'warning' : 'success'} />
        <Stat label="Substitutions Assigned" value={d?.substitutionsAssigned ?? '—'} icon={<CheckCircle2 size={18} />} tone="success" />
        <Stat label="Pending Substitutions" value={d?.pendingSubstitutions ?? '—'} icon={<AlertTriangle size={18} />} tone={(d?.pendingSubstitutions ?? 0) > 0 ? 'danger' : 'success'} />
        <Stat label="Classes Without Teacher" value={d?.classesWithoutTeacher ?? '—'} icon={<Radio size={18} />} tone={(d?.classesWithoutTeacher ?? 0) > 0 ? 'danger' : 'success'} />
      </div>

      {/* Absent teacher picker */}
      <Card>
        <CardHeader><CardTitle className="text-base">Absent teacher</CardTitle></CardHeader>
        <CardBody>
          {absentQ.isLoading ? (
            <div className="flex items-center gap-2 text-slate-500"><Spinner /> Detecting absent teachers…</div>
          ) : absentQ.data && absentQ.data.length === 0 ? (
            <p className="text-sm text-slate-500">No teachers are absent today. 🎉</p>
          ) : (
            <select
              className="w-full max-w-md rounded-lg border border-slate-300 px-3 py-2 text-sm"
              value={teacherId}
              onChange={(e) => setTeacherId(e.target.value)}
            >
              <option value="">— select an absent teacher —</option>
              {absentQ.data?.map((t) => (
                <option key={t.staffId} value={t.staffId}>{t.name} — {t.reason}</option>
              ))}
            </select>
          )}
        </CardBody>
      </Card>

      {/* Plan */}
      {planQ.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner /> Building plan…</div>}
      {planQ.isError && <ErrorBanner error={planQ.error} onRetry={() => planQ.refetch()} />}
      {plan && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-base">
                {plan.absentTeacherName} — {plan.periods.length} affected period{plan.periods.length !== 1 ? 's' : ''}
              </CardTitle>
              <Button onClick={() => autoAssign.mutate()} disabled={autoAssign.isPending || uncovered === 0}>
                <Wand2 size={15} className="mr-1" />
                {autoAssign.isPending ? 'Assigning…' : 'Auto Assign All Substitutes'}
              </Button>
            </div>
            {plan.isClassTeacher && (
              <p className="mt-2 text-xs text-amber-600">
                ⚠ Also class teacher of {plan.classTeacherOf.join(', ')} — assign a temporary class teacher for today.
              </p>
            )}
          </CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            {plan.periods.length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-400">This teacher has no classes scheduled today.</p>
            ) : (
              <table className="w-full text-sm min-w-[760px]">
                <thead className="border-b border-slate-100">
                  <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                    <th className="px-4 py-3 font-medium">Period</th>
                    <th className="px-4 py-3 font-medium">Class</th>
                    <th className="px-4 py-3 font-medium">Subject</th>
                    <th className="px-4 py-3 font-medium">Substitute</th>
                    <th className="px-4 py-3 font-medium" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {plan.periods.map((p) => (
                    <tr key={p.periodId} className="hover:bg-slate-50/60">
                      <td className="px-4 py-3 whitespace-nowrap">
                        <div className="font-medium">{p.periodName}</div>
                        <div className="text-xs text-slate-400">{hhmm(p.startTime)}–{hhmm(p.endTime)}</div>
                      </td>
                      <td className="px-4 py-3">{p.sectionLabel}</td>
                      <td className="px-4 py-3">{p.subjectName}</td>
                      <td className="px-4 py-3">
                        {p.alreadyCovered ? (
                          <Badge tone="success">Covered</Badge>
                        ) : p.candidates.length === 0 ? (
                          <Badge tone="danger">No free teacher</Badge>
                        ) : (
                          <select
                            className="w-full max-w-[260px] rounded-lg border border-slate-300 px-2 py-1.5 text-sm"
                            value={picks[p.periodId] ?? ''}
                            onChange={(e) => setPicks((m) => ({ ...m, [p.periodId]: e.target.value }))}
                          >
                            {p.candidates.map((c) => (
                              <option key={c.staffId} value={c.staffId}>
                                {c.recommended ? '★ ' : ''}{c.name}
                                {c.sameSubject ? ' · same subject' : c.teachesClass ? ' · knows class' : ` · ${c.periodsPerWeek}p/wk`}
                              </option>
                            ))}
                          </select>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {!p.alreadyCovered && p.candidates.length > 0 && (
                          <Button size="sm" variant="ghost"
                            disabled={assignOne.isPending || !picks[p.periodId]}
                            onClick={() => assignOne.mutate(p)}>
                            Assign
                          </Button>
                        )}
                      </td>
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
