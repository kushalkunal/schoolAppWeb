'use client';

/**
 * Fee configuration — simplified step-by-step wizard.
 *
 * Step 1 – Create / select a session (e.g. "2026-27 Fees").
 * Step 2 – For each class, toggle fee types on/off and enter amounts.
 *           Custom fee types can be created inline.
 * Step 3 – Go live. Every student enrolled in a class is auto-billed.
 *
 * Cashiers (FEE_WRITER but not FEE_CONFIG_EDITOR) see a read-only view.
 */

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CheckCircle2, ChevronDown, Plus, Save, Send, Settings2, X,
} from 'lucide-react';
import {
  feeStructureApi,
  type FeeStructureVersionResponse,
  type FeeHeadResponse,
  type MatrixRowDto,
} from '@/api/endpoints/feeStructure';
import { schoolApi } from '@/api/endpoints/school';
import type { ClassResponse } from '@/types/domain';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { FEE_WRITER, RequireRole, useHasRole } from '@/auth/RequireRole';
import { formatINR, cn } from '@/lib/utils';

export default function FeeStructurePage() {
  return (
    <RequireRole roles={FEE_WRITER}>
      <FeeConfigInner />
    </RequireRole>
  );
}

// ====================================================================
// Helpers
// ====================================================================

/** Collapse any per-term rows into a single annual row per class×feeHead. */
function aggregateToAnnual(source: MatrixRowDto[]): MatrixRowDto[] {
  const map = new Map<string, MatrixRowDto>();
  for (const r of source) {
    const key = `${r.classId}::${r.feeHeadId}`;
    const existing = map.get(key);
    if (!existing) {
      map.set(key, { ...r, termNumber: null });
    } else {
      map.set(key, { ...existing, amountPaise: existing.amountPaise + r.amountPaise });
    }
  }
  return Array.from(map.values());
}

// ====================================================================
// Main inner component
// ====================================================================

function FeeConfigInner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const { success, error: toastError } = useToast();
  const canEdit = useHasRole('SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN');

  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  // Local working copy — always annual (termNumber: null)
  const [rows, setRows] = useState<MatrixRowDto[]>([]);
  const [dirty, setDirty] = useState(false);

  // ---------- Queries ----------
  const versionsQ = useQuery({
    queryKey: ['fee-structure-versions', tenantId],
    queryFn: () => feeStructureApi.listVersions(tenantId),
    enabled: !!tenantId,
  });

  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  const headsQ = useQuery({
    queryKey: ['fee-heads', tenantId],
    queryFn: () => feeStructureApi.listFeeHeads(tenantId),
    enabled: !!tenantId,
  });

  const yearQ = useQuery({
    queryKey: ['academic-year-current', tenantId],
    queryFn: () => feeStructureApi.currentAcademicYear(tenantId),
    enabled: !!tenantId,
  });

  const matrixQ = useQuery({
    queryKey: ['fee-structure-matrix', tenantId, selectedVersionId],
    queryFn: () => feeStructureApi.getMatrix(tenantId, selectedVersionId!),
    enabled: !!selectedVersionId,
  });

  // Auto-select ACTIVE (preferred) or first version
  useEffect(() => {
    if (selectedVersionId || !versionsQ.data?.length) return;
    const active = versionsQ.data.find((v) => v.status === 'ACTIVE');
    setSelectedVersionId(active?.id ?? versionsQ.data[0]!.id);
  }, [versionsQ.data, selectedVersionId]);

  // Sync local state when matrix loads. Aggregate any existing term rows → annual.
  useEffect(() => {
    if (matrixQ.data) {
      setRows(aggregateToAnnual(matrixQ.data.rows));
      setDirty(false);
    }
  }, [matrixQ.data]);

  // ---------- Mutations ----------
  const saveMutation = useMutation({
    mutationFn: () =>
      feeStructureApi.updateMatrix(tenantId, selectedVersionId!, { terms: [], rows }),
    onSuccess: (m) => {
      qc.setQueryData(['fee-structure-matrix', tenantId, selectedVersionId], m);
      setDirty(false);
      success('Saved successfully');
    },
    onError: (e: Error) => toastError(e.message || 'Save failed'),
  });

  const activateMutation = useMutation({
    mutationFn: async () => {
      if (dirty) {
        await feeStructureApi.updateMatrix(tenantId, selectedVersionId!, { terms: [], rows });
      }
      return feeStructureApi.activate(tenantId, selectedVersionId!);
    },
    onSuccess: () => {
      setDirty(false);
      success('Fee configuration is now live! Enrolled students see fees for their class automatically.');
      qc.invalidateQueries({ queryKey: ['fee-structure-versions', tenantId] });
      qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, selectedVersionId] });
    },
    onError: (e: Error) => toastError(e.message || 'Activation failed'),
  });

  const archiveMutation = useMutation({
    mutationFn: () => feeStructureApi.archive(tenantId, selectedVersionId!),
    onSuccess: () => {
      success('Session archived');
      qc.invalidateQueries({ queryKey: ['fee-structure-versions', tenantId] });
    },
    onError: (e: Error) => toastError(e.message || 'Archive failed'),
  });

  // ---------- Loading / error ----------
  if (versionsQ.isLoading || classesQ.isLoading || headsQ.isLoading) {
    return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>;
  }
  if (versionsQ.isError) {
    return <ErrorBanner error={versionsQ.error} onRetry={() => versionsQ.refetch()} />;
  }

  const versions = versionsQ.data ?? [];
  const classes = classesQ.data ?? [];
  const heads = headsQ.data ?? [];
  const currentVersion = versions.find((v) => v.id === selectedVersionId);
  const readOnly = !canEdit || currentVersion?.status !== 'DRAFT';

  // ---------- Step 1: No sessions yet ----------
  if (versions.length === 0) {
    return (
      <div className="space-y-6 max-w-xl">
        <PageHeader
          icon={<Settings2 size={18} />}
          title="Fee configuration"
          description="Set up fees for each class — students are billed automatically when enrolled."
        />

        <div className="space-y-4">
          <StepRow n={1} title="Create a session"
            desc={`e.g. "2026–27 Fees" for the current academic year`} active />
          <StepRow n={2} title="Configure fees per class"
            desc="Enable fee types and set amounts — applies to all sections automatically" active={false} />
          <StepRow n={3} title="Go live"
            desc="Cashiers see the schedule; newly enrolled students are auto-billed" active={false} />
        </div>

        {canEdit && (
          <Button onClick={() => setCreateOpen(true)}>
            <Plus size={14} className="mr-1.5" /> Create session
          </Button>
        )}

        {createOpen && canEdit && (
          <CreateVersionModal
            tenantId={tenantId}
            defaultYearId={yearQ.data?.id ?? null}
            defaultYearName={yearQ.data?.name ?? null}
            onClose={() => setCreateOpen(false)}
            onCreated={(v) => {
              setCreateOpen(false);
              setSelectedVersionId(v.id);
              qc.invalidateQueries({ queryKey: ['fee-structure-versions', tenantId] });
            }}
            onError={toastError}
          />
        )}
      </div>
    );
  }

  // ---------- Main view ----------
  return (
    <div className="space-y-4 max-w-2xl">
      <PageHeader
        icon={<Settings2 size={18} />}
        title="Fee configuration"
        description="Fees set per class apply to all sections automatically."
      />

      {/* ── Session bar ── */}
      <Card>
        <CardBody className="py-4 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide shrink-0">
                Session
              </span>
              <select
                className="text-sm font-medium border border-slate-200 rounded-lg px-3 py-1.5 bg-white text-slate-800 focus:outline-none focus:border-primary"
                value={selectedVersionId ?? ''}
                onChange={(e) => setSelectedVersionId(e.target.value)}
              >
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>{v.name}</option>
                ))}
              </select>
              {currentVersion && <StatusBadge status={currentVersion.status} />}
              {dirty && <span className="text-xs text-amber-600 font-medium">● unsaved changes</span>}
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              {canEdit && (
                <Button variant="ghost" size="sm" onClick={() => setCreateOpen(true)}>
                  <Plus size={13} className="mr-1" /> New session
                </Button>
              )}
              {!readOnly && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => saveMutation.mutate()}
                  disabled={!dirty || saveMutation.isPending}
                >
                  <Save size={13} className="mr-1" />
                  {saveMutation.isPending ? 'Saving…' : 'Save draft'}
                </Button>
              )}
              {currentVersion?.status === 'DRAFT' && canEdit && (
                <Button
                  size="sm"
                  onClick={() => activateMutation.mutate()}
                  disabled={activateMutation.isPending}
                >
                  <CheckCircle2 size={13} className="mr-1" />
                  {activateMutation.isPending ? 'Going live…' : 'Go live'}
                </Button>
              )}
              {currentVersion?.status === 'ACTIVE' && canEdit && matrixQ.data && (
                <GenerateButton
                  tenantId={tenantId}
                  versionId={selectedVersionId!}
                  onSuccess={success}
                  onError={toastError}
                />
              )}
            </div>
          </div>

          {currentVersion?.status === 'ACTIVE' && (
            <div className="flex items-center gap-2 text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2">
              <CheckCircle2 size={13} className="shrink-0" />
              <span>
                Live — students enrolled in any class are billed automatically per this schedule.
              </span>
              {canEdit && (
                <button
                  className="ml-auto text-emerald-600 font-medium hover:underline shrink-0"
                  onClick={() => archiveMutation.mutate()}
                  disabled={archiveMutation.isPending}
                >
                  Archive
                </button>
              )}
            </div>
          )}
          {!canEdit && (
            <div className="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
              Read-only view · Contact the Principal to make changes.
            </div>
          )}
        </CardBody>
      </Card>

      {matrixQ.isLoading && (
        <div className="flex items-center gap-2 text-slate-500 text-sm py-4">
          <Spinner /> Loading fee schedule…
        </div>
      )}
      {matrixQ.isError && (
        <ErrorBanner error={matrixQ.error} onRetry={() => matrixQ.refetch()} />
      )}

      {matrixQ.data && (
        <div className="space-y-2">
          <div className="flex items-center justify-between px-0.5">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Classes
            </p>
            <p className="text-xs text-slate-400">
              Expand a class to set fees · applies to all sections
            </p>
          </div>

          {classes.length === 0 ? (
            <Card>
              <CardBody className="py-10 text-center text-sm text-slate-500">
                No classes found. Add classes first in Settings → Classes.
              </CardBody>
            </Card>
          ) : (
            classes.map((cls) => (
              <ClassAccordion
                key={cls.id}
                tenantId={tenantId}
                cls={cls}
                heads={heads}
                rows={rows}
                readOnly={readOnly}
                onChange={(updated) => { setRows(updated); setDirty(true); }}
                onHeadsRefresh={() =>
                  qc.invalidateQueries({ queryKey: ['fee-heads', tenantId] })
                }
              />
            ))
          )}
        </div>
      )}

      {createOpen && canEdit && (
        <CreateVersionModal
          tenantId={tenantId}
          defaultYearId={yearQ.data?.id ?? null}
          defaultYearName={yearQ.data?.name ?? null}
          onClose={() => setCreateOpen(false)}
          onCreated={(v) => {
            setCreateOpen(false);
            setSelectedVersionId(v.id);
            qc.invalidateQueries({ queryKey: ['fee-structure-versions', tenantId] });
            success(`Session "${v.name}" created — configure fees for each class, then go live`);
          }}
          onError={toastError}
        />
      )}
    </div>
  );
}

// ====================================================================
// Step row (empty-state guidance)
// ====================================================================

function StepRow({ n, title, desc, active }: {
  n: number; title: string; desc: string; active: boolean;
}) {
  return (
    <div className={cn('flex items-start gap-3', !active && 'opacity-35')}>
      <div className={cn(
        'w-7 h-7 rounded-full text-sm font-bold flex items-center justify-center shrink-0 mt-0.5',
        active ? 'bg-primary text-white' : 'bg-slate-200 text-slate-500',
      )}>
        {n}
      </div>
      <div>
        <div className="font-medium text-slate-800 text-sm">{title}</div>
        <div className="text-xs text-slate-500 mt-0.5">{desc}</div>
      </div>
    </div>
  );
}

// ====================================================================
// Class accordion — inline fee editor per class
// ====================================================================

function ClassAccordion({
  tenantId, cls, heads, rows, readOnly, onChange, onHeadsRefresh,
}: {
  tenantId: string;
  cls: ClassResponse;
  heads: FeeHeadResponse[];
  rows: MatrixRowDto[];
  readOnly: boolean;
  onChange: (rows: MatrixRowDto[]) => void;
  onHeadsRefresh: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [addingCustom, setAddingCustom] = useState(false);
  const [customName, setCustomName] = useState('');
  const customInputRef = useRef<HTMLInputElement>(null);
  const { success, error: toastError } = useToast();

  const classRows = rows.filter((r) => r.classId === cls.id);
  const enabledHeadIds = new Set(classRows.map((r) => r.feeHeadId));
  const configuredHeads = heads.filter((h) => h.active && enabledHeadIds.has(h.id));
  const totalPaise = classRows.reduce((s, r) => s + r.amountPaise, 0);
  const activeHeads = heads.filter((h) => h.active);

  const createHeadMutation = useMutation({
    mutationFn: (name: string) => feeStructureApi.createFeeHead(tenantId, name),
    onSuccess: (newHead) => {
      onHeadsRefresh();
      setCustomName('');
      setAddingCustom(false);
      success(`"${newHead.name}" added — you can now enable it for any class`);
    },
    onError: (e: Error) => toastError(e.message || 'Failed to create fee type'),
  });

  useEffect(() => {
    if (addingCustom) customInputRef.current?.focus();
  }, [addingCustom]);

  function toggleHead(headId: string, enabled: boolean) {
    if (!enabled) {
      onChange(rows.filter((r) => !(r.classId === cls.id && r.feeHeadId === headId)));
    } else if (!rows.some((r) => r.classId === cls.id && r.feeHeadId === headId)) {
      onChange([
        ...rows,
        { classId: cls.id, feeHeadId: headId, termNumber: null, amountPaise: 0, optional: false },
      ]);
    }
  }

  function setAmount(headId: string, value: string) {
    const paise = Math.max(0, Math.round((parseFloat(value) || 0) * 100));
    const idx = rows.findIndex(
      (r) => r.classId === cls.id && r.feeHeadId === headId && r.termNumber === null,
    );
    if (idx === -1) {
      if (paise > 0) {
        onChange([
          ...rows,
          { classId: cls.id, feeHeadId: headId, termNumber: null, amountPaise: paise, optional: false },
        ]);
      }
    } else {
      const next = [...rows];
      next[idx] = { ...next[idx]!, amountPaise: paise };
      onChange(next);
    }
  }

  function getAmount(headId: string): string {
    const r = rows.find(
      (x) => x.classId === cls.id && x.feeHeadId === headId && x.termNumber === null,
    );
    return r && r.amountPaise > 0 ? String(r.amountPaise / 100) : '';
  }

  function submitCustom() {
    const trimmed = customName.trim();
    if (trimmed) createHeadMutation.mutate(trimmed);
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
      {/* ── Header ── */}
      <button
        className="w-full text-left px-4 py-3.5 flex items-center justify-between gap-3 hover:bg-slate-50/60 transition"
        onClick={() => setOpen((v) => !v)}
      >
        <div className="min-w-0">
          <span className="font-semibold text-slate-900">{cls.name}</span>
          <span className="text-xs text-slate-400 ml-2">
            {cls.sections.length} section{cls.sections.length !== 1 ? 's' : ''}
          </span>
          {cls.sections.length > 0 && (
            <span className="text-xs text-slate-300 ml-1">
              ({cls.sections.map((s) => s.name).join(', ')})
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {configuredHeads.length > 0 ? (
            <span className="text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 rounded-full">
              {configuredHeads.length} type{configuredHeads.length !== 1 ? 's' : ''} · {formatINR(totalPaise)}/yr
            </span>
          ) : (
            <span className="text-xs text-slate-400 bg-slate-100 px-2.5 py-0.5 rounded-full">
              Not configured
            </span>
          )}
          <ChevronDown
            size={14}
            className={cn('text-slate-400 transition-transform shrink-0', open && 'rotate-180')}
          />
        </div>
      </button>

      {/* ── Expanded inline editor ── */}
      {open && (
        <div className="border-t border-slate-100 px-4 pb-4 pt-3 space-y-2 bg-slate-50/40">
          {activeHeads.length === 0 ? (
            <p className="text-sm text-slate-500 py-2">
              No fee types yet — add a custom fee type below.
            </p>
          ) : (
            activeHeads.map((head) => {
              const enabled = enabledHeadIds.has(head.id);
              return (
                <div
                  key={head.id}
                  className={cn(
                    'flex items-center gap-3 rounded-lg border px-3 py-2.5 bg-white transition',
                    enabled ? 'border-primary/30' : 'border-slate-200',
                  )}
                >
                  <input
                    type="checkbox"
                    id={`${cls.id}-${head.id}`}
                    checked={enabled}
                    disabled={readOnly}
                    onChange={(e) => toggleHead(head.id, e.target.checked)}
                    className="w-4 h-4 accent-primary shrink-0 cursor-pointer disabled:cursor-not-allowed"
                  />
                  <label
                    htmlFor={readOnly ? undefined : `${cls.id}-${head.id}`}
                    className={cn(
                      'flex-1 text-sm font-medium select-none min-w-0',
                      enabled ? 'text-slate-800' : 'text-slate-400',
                      !readOnly && 'cursor-pointer',
                    )}
                  >
                    {head.name}
                  </label>
                  {enabled ? (
                    <div className="flex items-center border border-slate-200 rounded-lg bg-white shrink-0 focus-within:border-primary transition">
                      <span className="px-2 text-slate-400 text-sm select-none">₹</span>
                      <input
                        type="number"
                        min={0}
                        step="1"
                        placeholder="0"
                        disabled={readOnly}
                        value={getAmount(head.id)}
                        onChange={(e) => setAmount(head.id, e.target.value)}
                        className="w-28 py-1.5 pr-3 text-sm focus:outline-none bg-transparent disabled:text-slate-500"
                      />
                    </div>
                  ) : (
                    <span className="text-xs text-slate-300 shrink-0">tick to enable</span>
                  )}
                </div>
              );
            })
          )}

          {/* ── Add custom fee type ── */}
          {!readOnly && (
            <div className="pt-1">
              {!addingCustom ? (
                <button
                  onClick={() => setAddingCustom(true)}
                  className="flex items-center gap-1.5 text-sm text-primary hover:underline font-medium px-1 min-h-[32px]"
                >
                  <Plus size={13} /> Add custom fee type
                </button>
              ) : (
                <div className="flex items-center gap-2 bg-white border border-primary/40 rounded-lg px-3 py-2">
                  <input
                    ref={customInputRef}
                    type="text"
                    placeholder="e.g. Sports Fee, Lab Fee, Caution Deposit…"
                    value={customName}
                    onChange={(e) => setCustomName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') submitCustom();
                      if (e.key === 'Escape') { setAddingCustom(false); setCustomName(''); }
                    }}
                    className="flex-1 text-sm outline-none bg-transparent"
                  />
                  <Button
                    size="sm"
                    onClick={submitCustom}
                    disabled={!customName.trim() || createHeadMutation.isPending}
                  >
                    {createHeadMutation.isPending ? 'Adding…' : 'Add'}
                  </Button>
                  <button
                    onClick={() => { setAddingCustom(false); setCustomName(''); }}
                    className="text-slate-400 hover:text-slate-600"
                  >
                    <X size={14} />
                  </button>
                </div>
              )}
            </div>
          )}

          {configuredHeads.length > 0 && (
            <div className="flex justify-between items-center px-1 pt-2 border-t border-slate-200 mt-1">
              <span className="text-xs text-slate-500">
                Annual total · all {cls.sections.length} section{cls.sections.length !== 1 ? 's' : ''}
              </span>
              <span className="text-sm font-semibold text-slate-800">{formatINR(totalPaise)}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ====================================================================
// Status badge
// ====================================================================

function StatusBadge({ status }: { status: FeeStructureVersionResponse['status'] }) {
  if (status === 'ACTIVE') {
    return (
      <span className="text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 rounded-full">
        Live
      </span>
    );
  }
  if (status === 'DRAFT') {
    return (
      <span className="text-xs font-medium text-amber-700 bg-amber-50 border border-amber-200 px-2.5 py-0.5 rounded-full">
        Draft
      </span>
    );
  }
  return (
    <span className="text-xs font-medium text-slate-500 bg-slate-100 border border-slate-200 px-2.5 py-0.5 rounded-full">
      Archived
    </span>
  );
}

// ====================================================================
// Generate invoices button (shown when ACTIVE)
// ====================================================================

function GenerateButton({
  tenantId, versionId, onSuccess, onError,
}: {
  tenantId: string;
  versionId: string;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [open, setOpen] = useState(false);

  const mutation = useMutation({
    mutationFn: () => feeStructureApi.generate(tenantId, versionId, { termNumber: null }),
    onSuccess: (r) => {
      setOpen(false);
      onSuccess(
        `Generated ${r.invoicesCreated} invoice${r.invoicesCreated === 1 ? '' : 's'}` +
        (r.invoicesSkippedDuplicate > 0
          ? ` · ${r.invoicesSkippedDuplicate} already existed`
          : ''),
      );
    },
    onError: (e: Error) => onError(e.message || 'Generation failed'),
  });

  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)}>
        <Send size={13} className="mr-1" /> Generate invoices
      </Button>
      {open && (
        <Modal open onClose={() => setOpen(false)} title="Generate fee invoices">
          <div className="space-y-3 text-sm">
            <p className="text-slate-600">
              Issues fee invoices for every enrolled student based on their class fees.
              Already-generated invoices are skipped — safe to re-run.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
              <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
                {mutation.isPending ? 'Generating…' : 'Generate'}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}

// ====================================================================
// Create session modal
// ====================================================================

function CreateVersionModal({
  tenantId, defaultYearId, defaultYearName, onClose, onCreated, onError,
}: {
  tenantId: string;
  defaultYearId: string | null;
  defaultYearName: string | null;
  onClose: () => void;
  onCreated: (v: FeeStructureVersionResponse) => void;
  onError: (msg: string) => void;
}) {
  const [name, setName] = useState(defaultYearName ? `${defaultYearName} Fees` : '');
  const [notes, setNotes] = useState('');

  const mutation = useMutation({
    mutationFn: () => {
      if (!defaultYearId) throw new Error('No current academic year configured');
      return feeStructureApi.createVersion(tenantId, {
        academicYearId: defaultYearId,
        name: name.trim(),
        notes: notes.trim() || undefined,
      });
    },
    onSuccess: onCreated,
    onError: (e: Error) => onError(e.message || 'Create failed'),
  });

  return (
    <Modal open onClose={onClose} title="New fee session">
      <div className="space-y-4 text-sm">
        <div>
          <p className="text-xs font-medium text-slate-500 mb-0.5">Academic year</p>
          <p className="text-slate-700 font-medium">{defaultYearName ?? '—'}</p>
        </div>
        <div>
          <label className="text-xs font-medium text-slate-500">Session name</label>
          <input
            autoFocus
            className="block w-full border border-slate-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:border-primary"
            placeholder="e.g. 2026–27 Fees"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div>
          <label className="text-xs font-medium text-slate-500">Notes (optional)</label>
          <textarea
            className="block w-full border border-slate-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:border-primary"
            rows={2}
            placeholder="Any notes about this session…"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!name.trim() || !defaultYearId || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? 'Creating…' : 'Create session'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

