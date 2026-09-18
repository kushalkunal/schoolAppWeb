'use client';

/**
 * Cashier fee-collection page.
 *
 * Flow:
 *   Step 1 — Find student: search by name/admission number  OR  browse by class
 *   Step 2 — Fee details: auto-loaded breakdown of pending invoices + total outstanding
 *   Step 3 — Collect: amount (pre-filled), payment mode, optional notes
 *   Step 4 — Success: receipt PDF link + resend options
 */

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import {
  AlertCircle, Banknote, CheckCircle2, ChevronDown, ChevronRight, ExternalLink,
  FileSpreadsheet, Plus, RotateCcw, Search, Trash2, Users,
} from 'lucide-react';
import { studentsApi } from '@/api/endpoints/students';
import { schoolApi } from '@/api/endpoints/school';
import { feesApi } from '@/api/endpoints/fees';
import { academicsApi } from '@/api/endpoints/academics';
import { feeStructureApi, type FeeHeadResponse, type MatrixResponse } from '@/api/endpoints/feeStructure';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Badge } from '@/components/ui/Badge';
import { useToast } from '@/components/ui/Toast';
import { formatINR, formatDate, cn } from '@/lib/utils';
import type { PaymentMode, QuickCollectRequest, StudentResponse } from '@/types/domain';

type BrowseMode = 'search' | 'class';

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];

/** 12 months of a session keyed by term number (1..12), labelled from the session start date. */
function monthsOf(startISO?: string): { term: number; label: string }[] {
  if (!startISO) return Array.from({ length: 12 }, (_, i) => ({ term: i + 1, label: `Month ${i + 1}` }));
  const start = new Date(startISO + 'T00:00:00');
  return Array.from({ length: 12 }, (_, i) => {
    const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
    return { term: i + 1, label: `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}` };
  });
}

export default function QuickCollectPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const toast = useToast();

  // ---------- student selection ----------
  const [browseMode, setBrowseMode] = useState<BrowseMode>('search');
  const [search, setSearch] = useState('');
  const [classFilter, setClassFilter] = useState('');
  const [sectionFilter, setSectionFilter] = useState('');
  const [selected, setSelected] = useState<StudentResponse | null>(null);

  // ---------- payment form ----------
  const [amountRupees, setAmountRupees] = useState('');
  const [mode, setMode] = useState<PaymentMode>('CASH');
  const [notes, setNotes] = useState('');

  // ---------- session + month filter ----------
  const [sessionFilter, setSessionFilter] = useState('');   // academicYearId ('' = all sessions)
  const [monthFilter, setMonthFilter] = useState('');       // termNumber as string ('' = all months)

  // ---------- bill builder state ----------
  // Which invoice IDs the cashier has checked for this collection
  const [selectedInvoiceIds, setSelectedInvoiceIds] = useState<Set<string>>(new Set());
  // Miscellaneous line items added by the cashier (e.g. Late Fine, ID Card)
  type MiscItem = { id: string; label: string; amount: string };
  const [miscItems, setMiscItems] = useState<MiscItem[]>([]);

  // ---------- data queries ----------
  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });

  const searchQ = useQuery({
    queryKey: ['students', tenantId, { search, size: 12 }],
    queryFn: () => studentsApi.list(tenantId, { search, size: 12 }),
    enabled: !!tenantId && browseMode === 'search' && search.length >= 2 && !selected,
  });

  const classStudentsQ = useQuery({
    queryKey: ['students', tenantId, { sectionId: sectionFilter, size: 100 }],
    queryFn: async () => {
      const items = await studentsApi.bySection(tenantId, sectionFilter);
      return { items };
    },
    enabled: !!tenantId && browseMode === 'class' && !!sectionFilter && !selected,
  });

  const summaryQ = useQuery({
    queryKey: ['fee-summary', tenantId, selected?.id],
    queryFn: () => feesApi.studentSummary(tenantId, selected!.id),
    enabled: !!tenantId && !!selected,
  });

  // Academic sessions for the session selector (+ month labels per session)
  const yearsQ = useQuery({
    queryKey: ['academic-years', tenantId],
    queryFn: () => academicsApi.listAcademicYears(tenantId),
    enabled: !!tenantId,
    staleTime: 10 * 60_000,
  });
  const sessions = yearsQ.data ?? [];
  const months = useMemo(
    () => monthsOf(sessions.find((y) => y.id === sessionFilter)?.startDate),
    [sessions, sessionFilter],
  );

  // A pending invoice matches the current session+month filter
  const matchesFilter = (i: { academicYearId: string | null; termNumber: number | null }) =>
    (!sessionFilter || i.academicYearId === sessionFilter)
    && (!monthFilter || i.termNumber === Number(monthFilter));

  // Fee heads for name resolution (Tuition Fee, Transport Fee, etc.)
  const feeHeadsQ = useQuery({
    queryKey: ['fee-heads', tenantId],
    queryFn: () => feeStructureApi.listFeeHeads(tenantId),
    enabled: !!tenantId,
    staleTime: 10 * 60_000,
  });
  const feeHeads = feeHeadsQ.data ?? [];

  // Student profile → get sectionId → derive classId for fee schedule reference
  const profileQ = useQuery({
    queryKey: ['student-profile', tenantId, selected?.id],
    queryFn: () => studentsApi.get(tenantId, selected!.id),
    enabled: !!selected,
    staleTime: 5 * 60_000,
  });

  const studentClassId = useMemo(() => {
    if (!profileQ.data?.currentEnrollment) return null;
    const sid = profileQ.data.currentEnrollment.sectionId;
    const cls = (classesQ.data ?? []).find((c) => c.sections.some((s) => s.id === sid));
    return cls?.id ?? null;
  }, [profileQ.data, classesQ.data]);

  // Active fee structure version → matrix for the student's class
  const feeVersionsQ = useQuery({
    queryKey: ['fee-structure-versions', tenantId],
    queryFn: () => feeStructureApi.listVersions(tenantId),
    enabled: !!selected,
    staleTime: 5 * 60_000,
  });
  const activeVersionId = useMemo(
    () => feeVersionsQ.data?.find((v) => v.status === 'ACTIVE')?.id ?? null,
    [feeVersionsQ.data],
  );
  const feeScheduleQ = useQuery({
    queryKey: ['fee-structure-matrix', tenantId, activeVersionId],
    queryFn: () => feeStructureApi.getMatrix(tenantId, activeVersionId!),
    enabled: !!activeVersionId && !!studentClassId,
    staleTime: 5 * 60_000,
  });

  // Pre-select pending invoices matching the session+month filter (re-runs when the filter changes,
  // so picking a month pulls exactly that month's dues and auto-checks them).
  useEffect(() => {
    if (!summaryQ.data) return;
    const pending = summaryQ.data.invoices
      .filter((i) => i.status === 'PENDING' || i.status === 'PARTIAL')
      .filter(matchesFilter);
    setSelectedInvoiceIds(new Set(pending.map((i) => i.id)));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summaryQ.data?.studentId, sessionFilter, monthFilter]);

  // Reset misc items only when the student changes
  useEffect(() => { setMiscItems([]); }, [summaryQ.data?.studentId]);

  // Auto-compute amount from checked invoices + misc items
  const computedTotal = useMemo(() => {
    const invTotal = (summaryQ.data?.invoices ?? [])
      .filter((i) => selectedInvoiceIds.has(i.id))
      .reduce((s, i) => s + i.balancePaise, 0);
    const miscTotal = miscItems.reduce(
      (s, m) => s + Math.round(parseFloat(m.amount || '0') * 100),
      0,
    );
    return invTotal + miscTotal;
  }, [summaryQ.data, selectedInvoiceIds, miscItems]);

  // Keep the amount input synced with the computed total
  useEffect(() => {
    if (computedTotal > 0) setAmountRupees((computedTotal / 100).toFixed(2));
    else setAmountRupees('');
  }, [computedTotal]);

  // ---------- collect mutation ----------
  const collect = useMutation({
    mutationFn: (req: QuickCollectRequest) => feesApi.quickCollect(tenantId, req),
    onSuccess: () => toast.success('Payment recorded — receipt sent to parent'),
  });

  function reset() {
    setSelected(null);
    setSearch('');
    setAmountRupees('');
    setMode('CASH');
    setNotes('');
    setSelectedInvoiceIds(new Set());
    setMiscItems([]);
    collect.reset();
  }

  function submit() {
    if (!selected) return;
    const amountPaise = Math.round(Number(amountRupees) * 100);
    if (!Number.isFinite(amountPaise) || amountPaise <= 0) return;
    // Build notes: misc item descriptions prepended, then the cashier's manual note
    const miscDesc = miscItems
      .filter((m) => m.label.trim() && Number(m.amount) > 0)
      .map((m) => `${m.label}: ₹${m.amount}`)
      .join(', ');
    const combinedNotes = [miscDesc, notes.trim()].filter(Boolean).join(' | ');
    const req: QuickCollectRequest = { studentId: selected.id, amountPaise, paymentMode: mode };
    if (combinedNotes) req.notes = combinedNotes;
    collect.mutate(req);
  }

  const classes = classesQ.data ?? [];
  const sections = useMemo(
    () => classes.find((c) => c.id === classFilter)?.sections ?? [],
    [classFilter, classes],
  );

  return (
    <div className="space-y-4 w-full max-w-2xl mx-auto pb-24 sm:pb-0">
      <div>
        <Link href={`/tenants/${tenantId}/fees/dashboard`}
              className="text-sm text-slate-500 hover:underline">
          ← Fees dashboard
        </Link>
        <h1 className="text-2xl font-semibold mt-1">Collect fee</h1>
        <p className="text-sm text-slate-500">Find a student, review dues, take payment.</p>
      </div>

      {/* ====== SUCCESS ====== */}
      {collect.isSuccess && collect.data && (
        <Card>
          <CardBody>
            <div className="flex items-start gap-3">
              <CheckCircle2 size={22} className="text-success mt-0.5 shrink-0" />
              <div className="flex-1 space-y-1">
                <p className="font-semibold text-slate-800">
                  {formatINR(collect.data.amountPaise)} collected · Receipt {collect.data.receiptNumber}
                </p>
                <p className="text-sm text-slate-500">
                  Outstanding balance: {formatINR(collect.data.outstandingBalancePaise)}
                </p>
                <div className="flex flex-wrap gap-2 mt-3">
                  {collect.data.receiptPdfUrl && (
                    <a href={collect.data.receiptPdfUrl} target="_blank" rel="noreferrer">
                      <Button size="sm" variant="secondary">
                        <ExternalLink size={13} /> View PDF receipt
                      </Button>
                    </a>
                  )}
                  <Button size="sm" variant="secondary" onClick={reset}>
                    <RotateCcw size={13} /> Collect another
                  </Button>
                </div>
              </div>
            </div>
          </CardBody>
        </Card>
      )}

      {collect.isError && <ErrorBanner error={collect.error} />}

      {/* ====== STEP 1: Find student ====== */}
      {!collect.isSuccess && (
        <Card>
          <CardHeader className="flex items-center justify-between gap-2 flex-wrap">
            <CardTitle>Step 1 · Find student</CardTitle>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => { setBrowseMode('search'); setClassFilter(''); setSectionFilter(''); }}
                className={`px-3 py-1 text-xs rounded-full transition ${browseMode === 'search' ? 'bg-primary text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
              >
                <Search size={11} className="inline mr-1" />Search
              </button>
              <button
                type="button"
                onClick={() => { setBrowseMode('class'); setSearch(''); }}
                className={`px-3 py-1 text-xs rounded-full transition ${browseMode === 'class' ? 'bg-primary text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
              >
                <Users size={11} className="inline mr-1" />Browse by class
              </button>
            </div>
          </CardHeader>
          <CardBody className="space-y-3">
            {selected ? (
              <div className="flex items-center justify-between p-3 border border-primary bg-primary/5 rounded-brand">
                <div>
                  <div className="font-medium text-slate-800">{selected.displayName}</div>
                  <div className="text-xs text-slate-500">{selected.admissionNumber ?? '—'}</div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => { setSelected(null); setAmountRupees(''); }}>
                  Change
                </Button>
              </div>
            ) : (
              <>
                {browseMode === 'search' ? (
                  <>
                    <Input
                      placeholder="Search by name or admission number…"
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      autoFocus
                    />
                    {searchQ.isFetching && <Spinner />}
                    <StudentList students={searchQ.data?.items ?? []} onSelect={setSelected} />
                  </>
                ) : (
                  <>
                    <div className="grid grid-cols-2 gap-2">
                      <label className="block">
                        <span className="text-xs font-medium text-slate-600">Class</span>
                        <select
                          className="mt-1 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary"
                          value={classFilter}
                          onChange={(e) => { setClassFilter(e.target.value); setSectionFilter(''); }}
                        >
                          <option value="">Select class</option>
                          {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                      </label>
                      <label className="block">
                        <span className="text-xs font-medium text-slate-600">Section</span>
                        <select
                          className="mt-1 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary"
                          value={sectionFilter}
                          onChange={(e) => setSectionFilter(e.target.value)}
                          disabled={!classFilter}
                        >
                          <option value="">Select section</option>
                          {sections.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                        </select>
                      </label>
                    </div>
                    {classStudentsQ.isFetching && <Spinner />}
                    <StudentList students={classStudentsQ.data?.items ?? []} onSelect={setSelected} />
                  </>
                )}
              </>
            )}
          </CardBody>
        </Card>
      )}

      {/* ====== STEP 2: Bill builder ====== */}
      {selected && !collect.isSuccess && (
        <Card>
          <CardHeader><CardTitle>Step 2 · Bill preview</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            {/* Session + month — pull a specific month's fee for collection */}
            <div className="grid grid-cols-2 gap-2">
              <label className="block">
                <span className="text-xs font-medium text-slate-600">Session</span>
                <select
                  className="mt-1 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary"
                  value={sessionFilter}
                  onChange={(e) => { setSessionFilter(e.target.value); setMonthFilter(''); }}
                >
                  <option value="">All sessions</option>
                  {sessions.map((y) => <option key={y.id} value={y.id}>{y.name}{y.current ? ' (current)' : ''}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-medium text-slate-600">Month</span>
                <select
                  className="mt-1 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary disabled:bg-slate-50"
                  value={monthFilter}
                  onChange={(e) => setMonthFilter(e.target.value)}
                >
                  <option value="">All months</option>
                  {months.map((m) => <option key={m.term} value={m.term}>{m.label}</option>)}
                </select>
              </label>
            </div>

            {summaryQ.isLoading && <Spinner />}
            {summaryQ.isError && (
              <p className="text-sm text-danger">Could not load fee details. Enter amount manually below.</p>
            )}
            {summaryQ.data && (
              <BillBuilder
                summary={summaryQ.data}
                feeHeads={feeHeads}
                filter={matchesFilter}
                selectedIds={selectedInvoiceIds}
                onToggle={(id) =>
                  setSelectedInvoiceIds((prev) => {
                    const next = new Set(prev);
                    next.has(id) ? next.delete(id) : next.add(id);
                    return next;
                  })
                }
                miscItems={miscItems}
                onMiscChange={(id, field, val) =>
                  setMiscItems((prev) =>
                    prev.map((m) => (m.id === id ? { ...m, [field]: val } : m)),
                  )
                }
                onMiscAdd={() =>
                  setMiscItems((prev) => [
                    ...prev,
                    { id: crypto.randomUUID(), label: '', amount: '' },
                  ])
                }
                onMiscRemove={(id) =>
                  setMiscItems((prev) => prev.filter((m) => m.id !== id))
                }
              />
            )}
          </CardBody>
        </Card>
      )}

      {/* ====== PAYMENT HISTORY (who collected) ====== */}
      {selected && !collect.isSuccess && summaryQ.data && summaryQ.data.recentPayments.length > 0 && (
        <Card>
          <CardHeader><CardTitle>Payment history</CardTitle></CardHeader>
          <CardBody className="p-0">
            <ul className="divide-y divide-slate-100 text-sm">
              {summaryQ.data.recentPayments.map((p) => (
                <li key={p.id} className="flex items-center justify-between px-4 py-2.5">
                  <div className="min-w-0">
                    <div className="font-medium text-slate-800 tabular-nums">{formatINR(p.amountPaise)}
                      <span className="ml-2 text-xs font-normal text-slate-400">{p.paymentMode}</span></div>
                    <div className="text-xs text-slate-500">
                      {formatDate(p.paymentDate)} · Receipt {p.receiptNumber}
                      {p.collectedByName && <> · Collected by <span className="font-medium text-slate-600">{p.collectedByName}</span></>}
                    </div>
                  </div>
                  {p.receiptPdfUrl && (
                    <a href={p.receiptPdfUrl} target="_blank" rel="noopener noreferrer"
                       className="text-xs text-primary hover:underline inline-flex items-center gap-1 shrink-0">
                      <ExternalLink size={12} /> Receipt
                    </a>
                  )}
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      )}

      {/* ====== FEE SCHEDULE REFERENCE (cashier view) ====== */}
      {selected && !collect.isSuccess && studentClassId && feeScheduleQ.data && (
        <FeeScheduleReference
          classId={studentClassId}
          className={profileQ.data?.currentEnrollment?.className ?? ''}
          matrix={feeScheduleQ.data}
          feeHeads={feeHeads}
        />
      )}

      {/* ====== STEP 3: Collect ====== */}
      {selected && !collect.isSuccess && (
        <Card>
          <CardHeader><CardTitle>Step 3 · Collect payment</CardTitle></CardHeader>
          <CardBody className="space-y-4">
            <Input
              label="Amount (₹)"
              value={amountRupees}
              onChange={(e) => setAmountRupees(e.target.value.replace(/[^0-9.]/g, ''))}
              type="text"
              inputMode="decimal"
              placeholder="0.00"
              className="text-2xl font-bold text-center tracking-tight"
            />

            <div>
              <div className="text-xs font-medium text-slate-600 uppercase tracking-wide mb-1.5">
                Payment mode
              </div>
              <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
                {(['CASH', 'ONLINE', 'CHEQUE', 'DD', 'BANK_TRANSFER'] as const).map((m) => (
                  <button
                    key={m}
                    type="button"
                    onClick={() => setMode(m)}
                    className={`py-3 text-xs rounded-brand border font-medium transition min-h-[48px] ${
                      mode === m
                        ? 'bg-primary text-white border-primary shadow-sm'
                        : 'border-slate-300 bg-white hover:bg-slate-50'
                    }`}
                  >
                    {m === 'BANK_TRANSFER' ? 'Bank' : m === 'ONLINE' ? 'UPI / Online' : m}
                  </button>
                ))}
              </div>
            </div>

            <Input
              label="Notes (optional)"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Term 2 fees, includes transport"
            />

            {/* Sticky collect button — visible in bottom bar on mobile, inline on desktop */}
            <div className="fixed bottom-0 left-0 right-0 sm:static sm:flex sm:justify-end
                            bg-white/95 backdrop-blur-sm border-t border-slate-200 p-3 sm:p-0 sm:border-0 sm:bg-transparent z-30">
              <Button
                onClick={submit}
                disabled={collect.isPending || !amountRupees || Number(amountRupees) <= 0}
                loading={collect.isPending}
                className="w-full sm:w-auto"
              >
                <Banknote size={15} />
                Collect &amp; send receipt
              </Button>
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

// -------------------------------------------------------
// Student list picker
// -------------------------------------------------------
function StudentList({
  students, onSelect,
}: { students: StudentResponse[]; onSelect: (s: StudentResponse) => void }) {
  if (students.length === 0) return null;
  return (
    <ul className="border border-slate-200 rounded-brand divide-y divide-slate-100 max-h-72 overflow-y-auto">
      {students.map((s) => (
        <li key={s.id}>
          <button
            type="button"
            onClick={() => onSelect(s)}
            className="w-full text-left px-4 py-3.5 hover:bg-slate-50 active:bg-slate-100 flex items-center justify-between group min-h-[56px]"
          >
            <div>
              <div className="text-sm font-medium text-slate-800">{s.displayName}</div>
              <div className="text-xs text-slate-500">{s.admissionNumber ?? '—'}</div>
            </div>
            <ChevronRight size={14} className="text-slate-400 group-hover:text-primary transition" />
          </button>
        </li>
      ))}
    </ul>
  );
}

// -------------------------------------------------------
// Bill builder — fee head breakdown with checkboxes + misc items
// -------------------------------------------------------
interface BillBuilderProps {
  summary: import('@/types/domain').StudentFeeSummaryResponse;
  feeHeads: FeeHeadResponse[];
  selectedIds: Set<string>;
  onToggle: (id: string) => void;
  miscItems: { id: string; label: string; amount: string }[];
  onMiscChange: (id: string, field: 'label' | 'amount', val: string) => void;
  onMiscAdd: () => void;
  onMiscRemove: (id: string) => void;
  filter?: (i: { academicYearId: string | null; termNumber: number | null }) => boolean;
}

function BillBuilder({
  summary, feeHeads, selectedIds, onToggle, miscItems, onMiscChange, onMiscAdd, onMiscRemove, filter,
}: BillBuilderProps) {
  const pending = summary.invoices
    .filter((i) => i.status === 'PENDING' || i.status === 'PARTIAL')
    .filter((i) => (filter ? filter(i) : true));
  const todayIso = new Date().toISOString().slice(0, 10);

  const headName = (feeHeadId: string | null, fallback: string | null) =>
    (feeHeadId ? feeHeads.find((h) => h.id === feeHeadId)?.name : null)
    ?? fallback ?? 'Fee';

  const selectedInvTotal = pending
    .filter((i) => selectedIds.has(i.id))
    .reduce((s, i) => s + i.balancePaise, 0);
  const miscTotal = miscItems.reduce(
    (s, m) => s + Math.round(parseFloat(m.amount || '0') * 100), 0,
  );
  const grandTotal = selectedInvTotal + miscTotal;

  if (pending.length === 0 && miscItems.length === 0) {
    return (
      <div className="text-sm text-success font-medium flex items-center gap-1.5">
        <CheckCircle2 size={15} /> No pending fees — all clear!
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="divide-y divide-slate-100 rounded-brand border border-slate-200 overflow-hidden text-sm">

        {/* ── Pre-set fee head rows (from principal's structure) ── */}
        {pending.map((inv) => {
          const overdue = !!(inv.dueDate && inv.dueDate < todayIso);
          const checked = selectedIds.has(inv.id);
          const name = headName(inv.feeHeadId, inv.description);
          return (
            <label key={inv.id}
              className="flex items-start gap-3 px-4 py-3 hover:bg-slate-50 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={checked}
                onChange={() => onToggle(inv.id)}
                className="mt-0.5 w-4 h-4 rounded accent-primary shrink-0"
              />
              <div className="flex-1 min-w-0">
                <div className={`font-medium leading-tight ${!checked ? 'text-slate-400 line-through' : 'text-slate-800'}`}>
                  {name}
                </div>
                {inv.dueDate && (
                  <div className={`text-xs mt-0.5 ${overdue ? 'text-danger' : 'text-slate-400'}`}>
                    Due {formatDate(inv.dueDate)}{overdue ? ' · Overdue' : ''}
                  </div>
                )}
                {inv.status === 'PARTIAL' && (
                  <div className="text-xs text-warning mt-0.5">
                    {formatINR(inv.amountPaidPaise)} already paid — balance shown
                  </div>
                )}
              </div>
              <div className={`text-right shrink-0 ${!checked ? 'text-slate-400' : ''}`}>
                <div className="font-semibold tabular-nums">{formatINR(inv.balancePaise)}</div>
                {inv.status === 'PARTIAL' && <Badge tone="warning" size="sm">Balance</Badge>}
              </div>
            </label>
          );
        })}

        {/* ── Miscellaneous items added by cashier ── */}
        {miscItems.map((item) => (
          <div key={item.id} className="flex items-center gap-2 px-4 py-2.5 bg-slate-50/70">
            {/* visual spacer to align with checkbox column */}
            <div className="w-4 shrink-0" />
            <input
              type="text"
              placeholder="Description (e.g. Late fine, ID card)"
              value={item.label}
              onChange={(e) => onMiscChange(item.id, 'label', e.target.value)}
              className="flex-1 min-w-0 text-sm border border-slate-200 rounded-brand px-2.5 py-1.5
                         focus:outline-none focus:border-primary placeholder:text-slate-400"
            />
            <div className="flex items-center border border-slate-200 rounded-brand bg-white shrink-0">
              <span className="px-2 text-slate-400 text-sm select-none">₹</span>
              <input
                type="number"
                min={0}
                inputMode="decimal"
                placeholder="0"
                value={item.amount}
                onChange={(e) => onMiscChange(item.id, 'amount', e.target.value)}
                className="w-20 text-sm py-1.5 pr-2 focus:outline-none bg-transparent"
              />
            </div>
            <button
              type="button"
              onClick={() => onMiscRemove(item.id)}
              className="p-1.5 text-slate-400 hover:text-danger rounded transition shrink-0"
              aria-label="Remove item"
            >
              <Trash2 size={14} />
            </button>
          </div>
        ))}

        {/* ── Grand total ── */}
        <div className="flex justify-between items-center px-4 py-3 bg-slate-50 font-semibold">
          <span className="text-slate-700">Total selected</span>
          <span className="text-lg tabular-nums">{formatINR(grandTotal)}</span>
        </div>
      </div>

      {/* Add misc item */}
      <button
        type="button"
        onClick={onMiscAdd}
        className="flex items-center gap-1.5 text-sm text-primary hover:underline font-medium px-1 min-h-[36px]"
      >
        <Plus size={14} strokeWidth={2.5} />
        Add item (late fine, damage, ID card…)
      </button>

      {/* Overdue warning */}
      {pending.some((i) => i.dueDate && i.dueDate < todayIso && selectedIds.has(i.id)) && (
        <div className="flex items-center gap-1.5 text-xs text-danger font-medium">
          <AlertCircle size={13} /> Selected invoices include overdue items — late fine may apply.
        </div>
      )}
    </div>
  );
}

// -------------------------------------------------------
// Fee schedule reference — read-only view of configured
// fee types and amounts for this student's class.
// Shown to cashiers as a reference when collecting.
// -------------------------------------------------------
function FeeScheduleReference({
  classId, className, matrix, feeHeads,
}: {
  classId: string;
  className: string;
  matrix: MatrixResponse;
  feeHeads: FeeHeadResponse[];
}) {
  const [expanded, setExpanded] = useState(false);

  const classRows = matrix.rows.filter((r) => r.classId === classId);
  if (classRows.length === 0) return null;

  const configuredHeadIds = [...new Set(classRows.map((r) => r.feeHeadId))];
  const configuredHeads = feeHeads.filter((h) => configuredHeadIds.includes(h.id));
  const termNumbers: (number | null)[] =
    matrix.terms.length > 0
      ? matrix.terms.map((t) => t.termNumber).sort((a, b) => a - b)
      : [null];

  return (
    <Card>
      <CardBody className="py-3">
        <button
          className="flex items-center justify-between w-full"
          onClick={() => setExpanded((v) => !v)}
        >
          <div className="flex items-center gap-2">
            <FileSpreadsheet size={15} className="text-slate-400 shrink-0" />
            <span className="text-sm font-medium text-slate-700">
              Fee schedule for {className}
            </span>
            <span className="text-xs text-slate-400">
              {configuredHeads.length} type{configuredHeads.length !== 1 ? 's' : ''}
              {' · '}
              {matrix.terms.length === 0 ? 'Annual' : `${matrix.terms.length} terms`}
            </span>
          </div>
          <ChevronDown
            size={14}
            className={cn('text-slate-400 transition-transform shrink-0', expanded && 'rotate-180')}
          />
        </button>

        {expanded && (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-500 border-b border-slate-200">
                  <th className="py-1.5 pr-3">Fee type</th>
                  {termNumbers.map((tn) => (
                    <th key={String(tn)} className="py-1.5 px-2 text-right">
                      {tn === null
                        ? 'Annual'
                        : matrix.terms.find((t) => t.termNumber === tn)?.name ?? `Term ${tn}`}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {configuredHeads.map((head) => (
                  <tr key={head.id} className="border-b border-slate-100 last:border-0">
                    <td className="py-1.5 pr-3 text-slate-700">{head.name}</td>
                    {termNumbers.map((tn) => {
                      const row = classRows.find(
                        (r) => r.feeHeadId === head.id && r.termNumber === tn,
                      );
                      return (
                        <td key={String(tn)} className="py-1.5 px-2 text-right font-medium text-slate-800">
                          {row ? formatINR(row.amountPaise) : '—'}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="text-xs text-slate-400 mt-2">
              Active fee config: {matrix.version.name}
            </p>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
