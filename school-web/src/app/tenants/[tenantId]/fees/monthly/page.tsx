'use client';

/**
 * Simplified Fee Configuration — the whole flow on one screen:
 *   1. pick session (version) · 2. pick class · 3. enter monthly fees · 4. Generate Fees.
 *
 * Built on the fee-structure matrix engine: the academic year's 12 months are the structure's
 * terms; each filled monthly cell is a (class × head × month) row; one-time charges are annual
 * rows (termNumber = null). A blank month = not charged then. Editing requires a DRAFT version;
 * "Generate Fees" activates it and creates every student's monthly + one-time invoices.
 */

import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { CalendarRange, Save, Rocket, Wand2, Copy, TrendingUp } from 'lucide-react';
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

function buildMonths(ayStartISO: string): TermDto[] {
  const start = new Date(ayStartISO + 'T00:00:00');
  const out: TermDto[] = [];
  for (let i = 0; i < 12; i++) {
    const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
    const y = d.getFullYear(), m = d.getMonth();
    const last = new Date(y, m + 1, 0).getDate();
    const p2 = (n: number) => String(n).padStart(2, '0');
    out.push({
      termNumber: i + 1, name: `${MONTH_NAMES[m]} ${y}`,
      startDate: `${y}-${p2(m + 1)}-01`, endDate: `${y}-${p2(m + 1)}-${p2(last)}`, dueDate: `${y}-${p2(m + 1)}-10`,
    });
  }
  return out;
}

export default function MonthlyFeePage() {
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
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

  const [versionId, setVersionId] = useState('');
  const [classId, setClassId] = useState('');
  const [rows, setRows] = useState<MatrixRowDto[]>([]);
  const [dirty, setDirty] = useState(false);

  const versions = versionsQ.data ?? [];
  const editable = versions.filter((v) => v.status !== 'ARCHIVED');
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
  const versionStatus = matrixQ.data?.version.status ?? 'DRAFT';
  const canEdit = versionStatus === 'DRAFT';

  const cell = (headId: string, term: number | null) =>
    rows.find((x) => x.classId === classId && x.feeHeadId === headId && x.termNumber === term);
  const amountFor = (headId: string, term: number | null) => {
    const r = cell(headId, term); return r && r.amountPaise > 0 ? String(r.amountPaise / 100) : '';
  };
  const setAmount = (headId: string, term: number | null, rupeesStr: string) => {
    const paise = Math.round((parseFloat(rupeesStr) || 0) * 100);
    setRows((prev) => {
      const others = prev.filter((x) => !(x.classId === classId && x.feeHeadId === headId && x.termNumber === term));
      return paise > 0 ? [...others, { classId, feeHeadId: headId, termNumber: term, amountPaise: paise, optional: false }] : others;
    });
    setDirty(true);
  };
  const applyAcross = (headId: string, rupeesStr: string) => months.forEach((m) => setAmount(headId, m.termNumber, rupeesStr));

  const [qfHead, setQfHead] = useState(''); const [qfFrom, setQfFrom] = useState(1); const [qfTo, setQfTo] = useState(12); const [qfAmt, setQfAmt] = useState('');
  const applyRange = () => {
    if (!qfHead || !qfAmt) return;
    months.filter((m) => m.termNumber >= qfFrom && m.termNumber <= qfTo).forEach((m) => setAmount(qfHead, m.termNumber, qfAmt));
    toast.success('Applied to selected months');
  };

  const [copyTarget, setCopyTarget] = useState(''); const [pct, setPct] = useState('');
  const copyToClass = () => {
    if (!copyTarget || copyTarget === classId) return;
    setRows((prev) => {
      const mine = prev.filter((x) => x.classId === classId);
      const withoutTarget = prev.filter((x) => x.classId !== copyTarget);
      return [...withoutTarget, ...mine.map((r) => ({ ...r, classId: copyTarget }))];
    });
    setDirty(true);
    toast.success('Copied this class’s fees to the selected class');
  };
  const increasePct = () => {
    const p = parseFloat(pct); if (!p) return;
    setRows((prev) => prev.map((r) => r.classId === classId ? { ...r, amountPaise: Math.round(r.amountPaise * (1 + p / 100)) } : r));
    setDirty(true);
    toast.success(`Increased this class’s fees by ${p}%`);
  };

  const save = useMutation({
    mutationFn: () => feeStructureApi.updateMatrix(tenantId, versionId, { terms: months, rows }),
    onSuccess: () => { setDirty(false); toast.success('Fees saved'); qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, versionId] }); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to save'),
  });

  const generate = useMutation({
    mutationFn: async () => {
      if (dirty) await feeStructureApi.updateMatrix(tenantId, versionId, { terms: months, rows });
      if (versionStatus === 'DRAFT') await feeStructureApi.activate(tenantId, versionId);
      return feeStructureApi.generate(tenantId, versionId, { termNumber: null });
    },
    onSuccess: (r) => {
      toast.success(`Generated ${r.invoicesCreated} invoice(s) for ${r.studentsProcessed} student(s)`);
      qc.invalidateQueries({ queryKey: ['fee-versions', tenantId] });
      qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, versionId] });
    },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Generate failed'),
  });

  if (versionsQ.isLoading || classesQ.isLoading || headsQ.isLoading || yearQ.isLoading)
    return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>;

  const classes = classesQ.data ?? [];
  const className = classes.find((c) => c.id === classId)?.name ?? '';
  const monthlyTotal = heads.reduce((s, h) => s + months.reduce((a, m) => a + (cell(h.id, m.termNumber)?.amountPaise ?? 0), 0), 0);
  const oneTimeTotal = heads.reduce((s, h) => s + (cell(h.id, null)?.amountPaise ?? 0), 0);

  return (
    <div className="space-y-5">
      <PageHeader title="Fee Configuration" description="Pick a session and class, enter the monthly fees, then Generate Fees. That's it." icon={<CalendarRange />} />

      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">Session</span>
          <select className="rounded-lg border border-slate-200 px-3 py-2 text-sm" value={versionId} onChange={(e) => setVersionId(e.target.value)}>
            {editable.map((v) => <option key={v.id} value={v.id}>{v.name} ({v.status})</option>)}
            {editable.length === 0 && <option value="">No session — create one in Fees → Structure</option>}
          </select>
        </label>
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">Class</span>
          <select className="rounded-lg border border-slate-200 px-3 py-2 text-sm" value={classId} onChange={(e) => setClassId(e.target.value)}>
            {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <div className="ml-auto flex items-center gap-3">
          <span className="text-xs text-slate-500">Monthly ₹{(monthlyTotal / 100).toLocaleString()} · One-time ₹{(oneTimeTotal / 100).toLocaleString()}</span>
          <Button variant="secondary" onClick={() => save.mutate()} disabled={!dirty || save.isPending || !canEdit}>
            <Save size={15} className="mr-1" />{save.isPending ? 'Saving…' : 'Save'}
          </Button>
          <Button onClick={() => { if (confirm('Activate this session and generate fees for all students in every class? Amounts become locked after this.')) generate.mutate(); }}
            disabled={generate.isPending || !versionId}>
            <Rocket size={15} className="mr-1" />{generate.isPending ? 'Generating…' : 'Generate Fees'}
          </Button>
        </div>
      </div>

      {!canEdit && <div className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-700">This session is {versionStatus} — amounts are locked. Create a new DRAFT session to change fees.</div>}
      {matrixQ.isError && <ErrorBanner error={matrixQ.error} onRetry={() => matrixQ.refetch()} />}

      {!versionId ? (
        <Card><CardBody className="py-8 text-center text-sm text-slate-500">Create a session first (Fees → Structure).</CardBody></Card>
      ) : heads.length === 0 ? (
        <Card><CardBody className="py-8 text-center text-sm text-slate-500">No fee types yet. Add them in Fees → Fee Heads.</CardBody></Card>
      ) : (
        <>
          {canEdit && (
            <Card><CardBody className="flex flex-wrap items-end gap-2 py-3">
              <Wand2 size={16} className="text-primary mb-2" />
              <label className="text-xs">Fee type<select className="block rounded border border-slate-200 px-2 py-1.5 text-sm" value={qfHead} onChange={(e) => setQfHead(e.target.value)}>
                <option value="">—</option>{heads.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}</select></label>
              <label className="text-xs">From<select className="block rounded border border-slate-200 px-2 py-1.5 text-sm" value={qfFrom} onChange={(e) => setQfFrom(+e.target.value)}>
                {months.map((m) => <option key={m.termNumber} value={m.termNumber}>{(m.name.split(' ')[0] ?? '').slice(0, 3)}</option>)}</select></label>
              <label className="text-xs">To<select className="block rounded border border-slate-200 px-2 py-1.5 text-sm" value={qfTo} onChange={(e) => setQfTo(+e.target.value)}>
                {months.map((m) => <option key={m.termNumber} value={m.termNumber}>{(m.name.split(' ')[0] ?? '').slice(0, 3)}</option>)}</select></label>
              <label className="text-xs">Amount ₹<input type="number" className="block w-24 rounded border border-slate-200 px-2 py-1.5 text-sm" value={qfAmt} onChange={(e) => setQfAmt(e.target.value)} /></label>
              <Button size="sm" variant="secondary" onClick={applyRange}>Apply to range</Button>
            </CardBody></Card>
          )}

          <Card>
            <CardHeader><CardTitle className="text-base">{className} — monthly fees (₹)</CardTitle></CardHeader>
            <CardBody className="p-0 overflow-x-auto">
              <table className="text-sm border-collapse">
                <thead><tr className="text-xs uppercase tracking-wide text-slate-400">
                  <th className="sticky left-0 bg-white px-3 py-2 text-left font-medium border-b border-slate-100 min-w-[150px]">Fee Type</th>
                  {months.map((m) => <th key={m.termNumber} className="px-2 py-2 font-medium border-b border-slate-100 text-center min-w-[80px]">{(m.name.split(' ')[0] ?? '').slice(0, 3)}</th>)}
                  <th className="px-2 py-2 font-medium border-b border-slate-100 text-center">All</th>
                </tr></thead>
                <tbody className="divide-y divide-slate-50">
                  {heads.map((h) => (
                    <tr key={h.id} className="hover:bg-slate-50/40">
                      <td className="sticky left-0 bg-white px-3 py-2 font-medium border-r border-slate-100">{h.name}</td>
                      {months.map((m) => (
                        <td key={m.termNumber} className="px-1 py-1">
                          <input type="number" min="0" disabled={!canEdit}
                            className="w-[68px] rounded border border-slate-200 px-2 py-1 text-right text-sm disabled:bg-slate-50"
                            value={amountFor(h.id, m.termNumber)} onChange={(e) => setAmount(h.id, m.termNumber, e.target.value)} placeholder="—" />
                        </td>
                      ))}
                      <td className="px-1 py-1"><input type="number" min="0" placeholder="all" disabled={!canEdit}
                        className="w-[68px] rounded border border-dashed border-slate-300 px-2 py-1 text-right text-sm disabled:bg-slate-50"
                        onChange={(e) => { if (e.target.value) applyAcross(h.id, e.target.value); }} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardBody>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-base">One-time charges (not billed monthly)</CardTitle></CardHeader>
            <CardBody className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {heads.map((h) => (
                <label key={h.id} className="flex items-center justify-between gap-2 rounded-lg border border-slate-100 px-3 py-2 text-sm">
                  <span>{h.name}</span>
                  <span className="flex items-center gap-1">₹<input type="number" min="0" disabled={!canEdit}
                    className="w-24 rounded border border-slate-200 px-2 py-1 text-right text-sm disabled:bg-slate-50"
                    value={amountFor(h.id, null)} onChange={(e) => setAmount(h.id, null, e.target.value)} placeholder="—" /></span>
                </label>
              ))}
            </CardBody>
          </Card>

          {canEdit && (
            <Card>
              <CardHeader><CardTitle className="text-base">Bulk operations</CardTitle></CardHeader>
              <CardBody className="flex flex-wrap items-end gap-4">
                <div className="flex items-end gap-2">
                  <Copy size={16} className="text-slate-400 mb-2" />
                  <label className="text-xs">Copy {className}&apos;s fees to<select className="block rounded border border-slate-200 px-2 py-1.5 text-sm" value={copyTarget} onChange={(e) => setCopyTarget(e.target.value)}>
                    <option value="">— select class —</option>{classes.filter((c) => c.id !== classId).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
                  <Button size="sm" variant="secondary" onClick={copyToClass} disabled={!copyTarget}>Copy</Button>
                </div>
                <div className="flex items-end gap-2">
                  <TrendingUp size={16} className="text-slate-400 mb-2" />
                  <label className="text-xs">Increase this class by %<input type="number" className="block w-20 rounded border border-slate-200 px-2 py-1.5 text-sm" value={pct} onChange={(e) => setPct(e.target.value)} /></label>
                  <Button size="sm" variant="secondary" onClick={increasePct} disabled={!pct}>Apply</Button>
                </div>
              </CardBody>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
