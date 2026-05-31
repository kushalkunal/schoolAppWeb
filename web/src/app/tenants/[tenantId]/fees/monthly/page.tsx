'use client';

/**
 * Monthly Fee Configuration.
 *
 * Per-class, per-month, per-fee-head amounts (the "month-wise" model real schools use). Built on
 * the fee-structure matrix engine: the academic year's 12 months are the structure's terms, and
 * each filled grid cell is a (class × head × month) matrix row. A blank cell = that fee isn't
 * charged that month (so "applicable months" falls out naturally). Admin/owner only.
 */

import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { CalendarRange, Save } from 'lucide-react';
import { feeStructureApi, type MatrixRowDto, type TermDto } from '@/api/endpoints/feeStructure';
import { schoolApi } from '@/api/endpoints/school';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];

/** 12 monthly terms starting at the academic-year start month (termNumber 1..12). */
function buildMonths(ayStartISO: string): TermDto[] {
  const start = new Date(ayStartISO + 'T00:00:00');
  const out: TermDto[] = [];
  for (let i = 0; i < 12; i++) {
    const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
    const y = d.getFullYear(), m = d.getMonth();
    const last = new Date(y, m + 1, 0).getDate();
    const p2 = (n: number) => String(n).padStart(2, '0');
    out.push({
      termNumber: i + 1,
      name: `${MONTH_NAMES[m]} ${y}`,
      startDate: `${y}-${p2(m + 1)}-01`,
      endDate: `${y}-${p2(m + 1)}-${p2(last)}`,
      dueDate: `${y}-${p2(m + 1)}-10`,
    });
  }
  return out;
}
const rowKey = (classId: string, headId: string, term: number) => `${classId}|${headId}|${term}`;

export default function MonthlyFeePage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <Inner />
    </RequireRole>
  );
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const versionsQ = useQuery({ queryKey: ['fee-versions', tenantId], queryFn: () => feeStructureApi.listVersions(tenantId), enabled: !!tenantId });
  const classesQ = useQuery({ queryKey: ['classes', tenantId], queryFn: () => schoolApi.listClasses(tenantId), enabled: !!tenantId });
  const headsQ = useQuery({ queryKey: ['fee-heads', tenantId], queryFn: () => feeStructureApi.listFeeHeads(tenantId), enabled: !!tenantId });
  const yearQ = useQuery({ queryKey: ['academic-year-current', tenantId], queryFn: () => feeStructureApi.currentAcademicYear(tenantId), enabled: !!tenantId });

  const [versionId, setVersionId] = useState<string>('');
  const [classId, setClassId] = useState<string>('');
  const [rows, setRows] = useState<MatrixRowDto[]>([]);   // working copy of the WHOLE matrix
  const [dirty, setDirty] = useState(false);

  const editable = versionsQ.data?.filter((v) => v.status !== 'ARCHIVED') ?? [];
  useEffect(() => { const f = editable[0]; if (!versionId && f) setVersionId(f.id); }, [editable, versionId]);
  useEffect(() => { const f = classesQ.data?.[0]; if (!classId && f) setClassId(f.id); }, [classesQ.data, classId]);

  const matrixQ = useQuery({
    queryKey: ['fee-structure-matrix', tenantId, versionId],
    queryFn: () => feeStructureApi.getMatrix(tenantId, versionId),
    enabled: !!tenantId && !!versionId,
  });
  useEffect(() => { if (matrixQ.data) { setRows(matrixQ.data.rows); setDirty(false); } }, [matrixQ.data]);

  const months = useMemo(() => (yearQ.data ? buildMonths(yearQ.data.startDate) : []), [yearQ.data]);
  const heads = (headsQ.data ?? []).filter((h) => h.active);

  const save = useMutation({
    mutationFn: () => feeStructureApi.updateMatrix(tenantId, versionId, { terms: months, rows }),
    onSuccess: () => { setDirty(false); toast.success('Monthly fees saved'); qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, versionId] }); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to save'),
  });

  function amountFor(headId: string, term: number): string {
    const r = rows.find((x) => x.classId === classId && x.feeHeadId === headId && x.termNumber === term);
    return r && r.amountPaise > 0 ? String(r.amountPaise / 100) : '';
  }
  function setAmount(headId: string, term: number, rupeesStr: string) {
    const paise = Math.round((parseFloat(rupeesStr) || 0) * 100);
    setRows((prev) => {
      const others = prev.filter((x) => !(x.classId === classId && x.feeHeadId === headId && x.termNumber === term));
      return paise > 0
        ? [...others, { classId, feeHeadId: headId, termNumber: term, amountPaise: paise, optional: false }]
        : others;   // blank/0 → not charged this month
    });
    setDirty(true);
  }
  function applyAcross(headId: string, rupeesStr: string) {
    months.forEach((m) => setAmount(headId, m.termNumber, rupeesStr));
  }

  if (versionsQ.isLoading || classesQ.isLoading || headsQ.isLoading || yearQ.isLoading) {
    return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>;
  }

  const classes = classesQ.data ?? [];
  const monthlyTotal = heads.reduce((sum, h) => sum + months.reduce((s, m) => {
    const r = rows.find((x) => x.classId === classId && x.feeHeadId === h.id && x.termNumber === m.termNumber);
    return s + (r?.amountPaise ?? 0);
  }, 0), 0);

  return (
    <div className="space-y-5">
      <PageHeader title="Monthly Fee Configuration" description="Set each class's fee per month and per fee head. Leave a month blank if that fee isn't charged that month." icon={<CalendarRange />} />

      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">Fee structure version</span>
          <select className="rounded-lg border border-slate-200 px-3 py-2 text-sm" value={versionId} onChange={(e) => setVersionId(e.target.value)}>
            {editable.map((v) => <option key={v.id} value={v.id}>{v.name} ({v.status})</option>)}
            {editable.length === 0 && <option value="">No version — create one in Fees → Structure</option>}
          </select>
        </label>
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">Class</span>
          <select className="rounded-lg border border-slate-200 px-3 py-2 text-sm" value={classId} onChange={(e) => setClassId(e.target.value)}>
            {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <div className="ml-auto flex items-center gap-3">
          <span className="text-sm text-slate-500">Monthly total: <strong>₹{(monthlyTotal / 100).toLocaleString()}</strong></span>
          <Button onClick={() => save.mutate()} disabled={!dirty || save.isPending || !versionId}>
            <Save size={15} className="mr-1" />{save.isPending ? 'Saving…' : 'Save monthly fees'}
          </Button>
        </div>
      </div>

      {matrixQ.isError && <ErrorBanner error={matrixQ.error} onRetry={() => matrixQ.refetch()} />}
      {!versionId ? (
        <Card><CardBody className="py-8 text-center text-sm text-slate-500">Create a fee-structure version first (Fees → Structure), then configure its monthly amounts here.</CardBody></Card>
      ) : heads.length === 0 ? (
        <Card><CardBody className="py-8 text-center text-sm text-slate-500">No fee heads yet. Add fee heads first.</CardBody></Card>
      ) : (
        <Card>
          <CardHeader><CardTitle className="text-base">{classes.find((c) => c.id === classId)?.name} — monthly fees (₹)</CardTitle></CardHeader>
          <CardBody className="p-0 overflow-x-auto">
            <table className="text-sm border-collapse">
              <thead>
                <tr className="text-xs uppercase tracking-wide text-slate-400">
                  <th className="sticky left-0 bg-white px-3 py-2 text-left font-medium border-b border-slate-100 min-w-[160px]">Fee Head</th>
                  {months.map((m) => (
                    <th key={m.termNumber} className="px-2 py-2 font-medium border-b border-slate-100 text-center min-w-[88px]">
                      {(m.name.split(' ')[0] ?? '').slice(0, 3)}
                    </th>
                  ))}
                  <th className="px-2 py-2 font-medium border-b border-slate-100 text-center">All</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {heads.map((h) => (
                  <tr key={h.id} className="hover:bg-slate-50/40">
                    <td className="sticky left-0 bg-white px-3 py-2 font-medium border-r border-slate-100">{h.name}</td>
                    {months.map((m) => (
                      <td key={m.termNumber} className="px-1 py-1">
                        <input
                          type="number" min="0" inputMode="numeric"
                          className="w-20 rounded border border-slate-200 px-2 py-1 text-right text-sm"
                          value={amountFor(h.id, m.termNumber)}
                          onChange={(e) => setAmount(h.id, m.termNumber, e.target.value)}
                          placeholder="—"
                        />
                      </td>
                    ))}
                    <td className="px-1 py-1">
                      <input
                        type="number" min="0" placeholder="set all"
                        className="w-20 rounded border border-dashed border-slate-300 px-2 py-1 text-right text-sm"
                        onChange={(e) => { if (e.target.value) applyAcross(h.id, e.target.value); }}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardBody>
        </Card>
      )}
      <p className="text-xs text-slate-400">
        Months come from the academic year. A blank month means that fee isn&apos;t charged then.
        After saving, generate monthly invoices from Fees → Structure (per term/month).
      </p>
    </div>
  );
}
