'use client';

/**
 * Fee-structure matrix editor — Slice 31b.
 *
 * Two-pane layout: the left rail lists every version (DRAFT / ACTIVE / ARCHIVED) so admins can
 * jump between sessions; the right pane edits the currently selected version's term schedule
 * and (class × fee head × term) cell amounts.
 *
 * Editing rules enforced by the backend (we mirror them in the UI for clarity):
 *   - Only DRAFT versions are editable. ACTIVE rows render read-only.
 *   - Activating a version auto-archives the previous ACTIVE for the same academic year.
 *   - "Generate invoices" is only available on ACTIVE versions.
 */

import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, FileSpreadsheet, Send, Archive, CheckCircle2, Save, Upload, Download } from 'lucide-react';
import {
  feeStructureApi,
  type FeeStructureVersionResponse,
  type MatrixImportRowResult,
  type MatrixResponse,
  type MatrixRowDto,
  type TermDto,
} from '@/api/endpoints/feeStructure';
import type { ImportResult } from '@/api/endpoints/imports';
import { schoolApi } from '@/api/endpoints/school';
import type { ClassResponse } from '@/types/domain';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { FEE_WRITER, RequireRole } from '@/auth/RequireRole';
import { formatINR, cn } from '@/lib/utils';

export default function FeeStructurePage() {
  return (
    <RequireRole roles={FEE_WRITER}>
      <FeeStructureInner />
    </RequireRole>
  );
}

function FeeStructureInner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const { success, error: toastError } = useToast();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

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

  // Auto-select first version when list loads (prefer ACTIVE).
  useEffect(() => {
    if (selectedId || !versionsQ.data || versionsQ.data.length === 0) return;
    const active = versionsQ.data.find((v) => v.status === 'ACTIVE');
    const fallback = versionsQ.data[0];
    if (active) setSelectedId(active.id);
    else if (fallback) setSelectedId(fallback.id);
  }, [versionsQ.data, selectedId]);

  if (versionsQ.isLoading || classesQ.isLoading || headsQ.isLoading) {
    return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>;
  }
  if (versionsQ.isError) return <ErrorBanner error={versionsQ.error} onRetry={() => versionsQ.refetch()} />;

  const versions = versionsQ.data ?? [];
  const classes = classesQ.data ?? [];
  const heads = headsQ.data ?? [];

  return (
    <div className="space-y-4">
      <PageHeader
        icon={<FileSpreadsheet size={18} />}
        title="Fee structure"
        description="Define per-class amounts and bulk-issue invoices for the academic year."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus size={14} className="mr-1" /> New version
          </Button>
        }
      />

      {versions.length === 0 ? (
        <EmptyState
          title="No fee structures yet"
          description="Create your first version, define class amounts, then activate and issue invoices in one click."
          action={<Button onClick={() => setCreateOpen(true)}>Create version</Button>}
        />
      ) : (
        <div className="grid grid-cols-12 gap-4">
          <div className="col-span-12 md:col-span-3">
            <VersionList
              versions={versions}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          </div>
          <div className="col-span-12 md:col-span-9">
            {selectedId ? (
              <MatrixEditor
                key={selectedId}
                tenantId={tenantId}
                versionId={selectedId}
                classes={classes}
                heads={heads}
                onChanged={() => {
                  qc.invalidateQueries({ queryKey: ['fee-structure-versions', tenantId] });
                }}
                onSuccess={success}
                onError={toastError}
              />
            ) : (
              <Card><CardBody className="text-sm text-slate-500">Select a version to edit.</CardBody></Card>
            )}
          </div>
        </div>
      )}

      {createOpen && (
        <CreateVersionModal
          tenantId={tenantId}
          defaultYearId={yearQ.data?.id ?? null}
          defaultYearName={yearQ.data?.name ?? null}
          onClose={() => setCreateOpen(false)}
          onCreated={(v) => {
            setCreateOpen(false);
            setSelectedId(v.id);
            qc.invalidateQueries({ queryKey: ['fee-structure-versions', tenantId] });
            success(`Created draft "${v.name}"`);
          }}
          onError={(msg) => toastError(msg)}
        />
      )}
    </div>
  );
}

// ===================================================================
// Version list (left rail)
// ===================================================================

function VersionList({
  versions, selectedId, onSelect,
}: {
  versions: FeeStructureVersionResponse[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <Card>
      <CardBody className="p-0">
        <ul className="divide-y divide-slate-100">
          {versions.map((v) => {
            const active = v.id === selectedId;
            return (
              <li key={v.id}>
                <button
                  onClick={() => onSelect(v.id)}
                  className={cn(
                    'w-full text-left px-3 py-2.5 hover:bg-slate-50 transition',
                    active && 'bg-primary-soft/60',
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <div className="font-medium text-sm text-slate-900 truncate">{v.name}</div>
                      <div className="text-[11px] text-slate-500 truncate">
                        {new Date(v.createdAt).toLocaleDateString()}
                      </div>
                    </div>
                    <StatusBadge status={v.status} />
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      </CardBody>
    </Card>
  );
}

function StatusBadge({ status }: { status: FeeStructureVersionResponse['status'] }) {
  if (status === 'ACTIVE')   return <Badge tone="success" size="sm">Active</Badge>;
  if (status === 'DRAFT')    return <Badge tone="warning" size="sm">Draft</Badge>;
  return <Badge tone="neutral" size="sm">Archived</Badge>;
}

// ===================================================================
// Matrix editor (right pane)
// ===================================================================

function MatrixEditor({
  tenantId, versionId, classes, heads, onChanged, onSuccess, onError,
}: {
  tenantId: string;
  versionId: string;
  classes: ClassResponse[];
  heads: { id: string; name: string; active: boolean }[];
  onChanged: () => void;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const qc = useQueryClient();

  const matrixQ = useQuery({
    queryKey: ['fee-structure-matrix', tenantId, versionId],
    queryFn: () => feeStructureApi.getMatrix(tenantId, versionId),
    enabled: !!versionId,
  });

  // Local working copy (so unsaved edits don't disappear on refetch).
  const [terms, setTerms] = useState<TermDto[]>([]);
  const [rows, setRows] = useState<MatrixRowDto[]>([]);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    if (matrixQ.data) {
      setTerms(matrixQ.data.terms);
      setRows(matrixQ.data.rows);
      setDirty(false);
    }
  }, [matrixQ.data]);

  const saveMutation = useMutation({
    mutationFn: () => feeStructureApi.updateMatrix(tenantId, versionId, { terms, rows }),
    onSuccess: (m: MatrixResponse) => {
      qc.setQueryData(['fee-structure-matrix', tenantId, versionId], m);
      setDirty(false);
      onSuccess('Matrix saved');
    },
    onError: (e: Error) => onError(e.message || 'Save failed'),
  });

  const activateMutation = useMutation({
    mutationFn: () => feeStructureApi.activate(tenantId, versionId),
    onSuccess: () => {
      onSuccess('Version activated');
      qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, versionId] });
      onChanged();
    },
    onError: (e: Error) => onError(e.message || 'Activate failed'),
  });

  const archiveMutation = useMutation({
    mutationFn: () => feeStructureApi.archive(tenantId, versionId),
    onSuccess: () => {
      onSuccess('Version archived');
      qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, versionId] });
      onChanged();
    },
    onError: (e: Error) => onError(e.message || 'Archive failed'),
  });

  if (matrixQ.isLoading) return <Card><CardBody><Spinner /> Loading matrix…</CardBody></Card>;
  if (matrixQ.isError)   return <ErrorBanner error={matrixQ.error} onRetry={() => matrixQ.refetch()} />;
  if (!matrixQ.data)     return null;

  const v = matrixQ.data.version;
  const readOnly = v.status !== 'DRAFT';

  // Term column headers: numeric terms + "Annual" pseudo-column.
  const termCols: (number | null)[] = [...terms.map((t) => t.termNumber).sort((a, b) => a - b), null];

  const setCell = (classId: string, feeHeadId: string, termNumber: number | null, amountRupees: number) => {
    const paise = Math.max(0, Math.round((amountRupees || 0) * 100));
    setDirty(true);
    setRows((prev) => {
      const existing = prev.findIndex(
        (r) => r.classId === classId && r.feeHeadId === feeHeadId && r.termNumber === termNumber,
      );
      if (paise === 0) {
        // Zero = remove the row (cleanliness — matches the backend's "omit zero" recommendation).
        if (existing === -1) return prev;
        const next = [...prev];
        next.splice(existing, 1);
        return next;
      }
      if (existing === -1) {
        return [...prev, { classId, feeHeadId, termNumber, amountPaise: paise, optional: false }];
      }
      const next = [...prev];
      const current = next[existing]!;
      next[existing] = { ...current, amountPaise: paise };
      return next;
    });
  };

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <Card>
        <CardBody className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-3 min-w-0">
            <h2 className="text-base font-semibold text-slate-900 truncate">{v.name}</h2>
            <StatusBadge status={v.status} />
            {dirty && <span className="text-xs text-amber-600">● unsaved changes</span>}
          </div>
          <div className="flex items-center gap-2">
            {!readOnly && (
              <Button
                variant="secondary"
                onClick={() => saveMutation.mutate()}
                disabled={!dirty || saveMutation.isPending}
              >
                <Save size={14} className="mr-1" />
                {saveMutation.isPending ? 'Saving…' : 'Save'}
              </Button>
            )}
            {v.status === 'DRAFT' && (
              <ImportCsvButton
                tenantId={tenantId}
                versionId={versionId}
                onSuccess={(msg) => {
                  onSuccess(msg);
                  qc.invalidateQueries({ queryKey: ['fee-structure-matrix', tenantId, versionId] });
                }}
                onError={onError}
              />
            )}
            {v.status === 'DRAFT' && (
              <Button
                onClick={() => activateMutation.mutate()}
                disabled={dirty || activateMutation.isPending}
                title={dirty ? 'Save changes before activating' : 'Activate this version'}
              >
                <CheckCircle2 size={14} className="mr-1" /> Activate
              </Button>
            )}
            {v.status === 'ACTIVE' && (
              <GenerateButton tenantId={tenantId} versionId={versionId} terms={terms}
                onSuccess={onSuccess} onError={onError} />
            )}
            {v.status !== 'ARCHIVED' && (
              <Button
                variant="ghost"
                onClick={() => archiveMutation.mutate()}
                disabled={archiveMutation.isPending}
              >
                <Archive size={14} className="mr-1" /> Archive
              </Button>
            )}
          </div>
        </CardBody>
      </Card>

      {/* Terms editor */}
      <Card>
        <CardBody>
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold text-slate-900">Term schedule</h3>
              <p className="text-xs text-slate-500">
                Leave empty for annual billing (one invoice per cell, due 30 days from issue).
              </p>
            </div>
            {!readOnly && (
              <Button
                variant="ghost"
                onClick={() => {
                  const next = [...terms];
                  const num = (next.length === 0 ? 1 : Math.max(...next.map((t) => t.termNumber)) + 1);
                  next.push({
                    termNumber: num,
                    name: `Term ${num}`,
                    startDate: new Date().toISOString().slice(0, 10),
                    endDate: new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10),
                    dueDate: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10),
                  });
                  setTerms(next);
                  setDirty(true);
                }}
              >
                <Plus size={14} className="mr-1" /> Add term
              </Button>
            )}
          </div>
          {terms.length === 0 ? (
            <p className="text-sm text-slate-500">No terms — billing is annual.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-slate-500 border-b border-slate-200">
                    <th className="py-2 pr-3">#</th>
                    <th className="py-2 pr-3">Name</th>
                    <th className="py-2 pr-3">Start</th>
                    <th className="py-2 pr-3">End</th>
                    <th className="py-2 pr-3">Due</th>
                    {!readOnly && <th></th>}
                  </tr>
                </thead>
                <tbody>
                  {terms.map((t, i) => (
                    <tr key={t.termNumber} className="border-b border-slate-100 last:border-0">
                      <td className="py-1.5 pr-3 text-slate-600">{t.termNumber}</td>
                      <td className="py-1.5 pr-3">
                        <input
                          className="w-full border border-slate-200 rounded px-2 py-1 text-sm disabled:bg-slate-50"
                          value={t.name}
                          disabled={readOnly}
                          onChange={(e) => {
                            const next = [...terms]; next[i] = { ...t, name: e.target.value };
                            setTerms(next); setDirty(true);
                          }}
                        />
                      </td>
                      {(['startDate', 'endDate', 'dueDate'] as const).map((k) => (
                        <td key={k} className="py-1.5 pr-3">
                          <input
                            type="date"
                            className="border border-slate-200 rounded px-2 py-1 text-sm disabled:bg-slate-50"
                            value={t[k]}
                            disabled={readOnly}
                            onChange={(e) => {
                              const next = [...terms]; next[i] = { ...t, [k]: e.target.value };
                              setTerms(next); setDirty(true);
                            }}
                          />
                        </td>
                      ))}
                      {!readOnly && (
                        <td className="py-1.5 pr-3">
                          <button
                            className="text-xs text-danger hover:underline"
                            onClick={() => {
                              setTerms((prev) => prev.filter((x) => x.termNumber !== t.termNumber));
                              setRows((prev) => prev.filter((r) => r.termNumber !== t.termNumber));
                              setDirty(true);
                            }}
                          >
                            Remove
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      {/* Matrix grid */}
      <Card>
        <CardBody>
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold text-slate-900">Amounts (₹ per cell)</h3>
              <p className="text-xs text-slate-500">
                One row per (class × fee head). Set 0 or leave blank to skip that combination.
              </p>
            </div>
            <div className="text-xs text-slate-500">
              Total: <span className="font-semibold text-slate-900">
                {formatINR(rows.reduce((acc, r) => acc + r.amountPaise, 0))}
              </span>
            </div>
          </div>
          {classes.length === 0 || heads.length === 0 ? (
            <EmptyState
              title="Need classes + fee heads first"
              description="Create at least one class (Settings → Classes) and one fee head (Settings → Fee heads) before defining the structure."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-slate-500 border-b border-slate-200 bg-slate-50">
                    <th className="py-2 px-3 sticky left-0 bg-slate-50">Class</th>
                    <th className="py-2 px-3">Fee head</th>
                    {termCols.map((tn) => (
                      <th key={String(tn)} className="py-2 px-3 text-right">
                        {tn === null ? 'Annual' : `Term ${tn}`}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {classes.flatMap((c) =>
                    heads.filter((h) => h.active).map((h) => (
                      <tr key={c.id + h.id} className="border-b border-slate-100 last:border-0">
                        <td className="py-1.5 px-3 sticky left-0 bg-white font-medium text-slate-800">{c.name}</td>
                        <td className="py-1.5 px-3 text-slate-600">{h.name}</td>
                        {termCols.map((tn) => {
                          const cell = rows.find(
                            (r) => r.classId === c.id && r.feeHeadId === h.id && r.termNumber === tn,
                          );
                          return (
                            <td key={String(tn)} className="py-1 px-2 text-right">
                              <input
                                type="number"
                                min={0}
                                step="1"
                                className={cn(
                                  'w-24 text-right border border-slate-200 rounded px-2 py-1 text-sm',
                                  'disabled:bg-slate-50 disabled:text-slate-500',
                                )}
                                placeholder="—"
                                disabled={readOnly}
                                value={cell ? cell.amountPaise / 100 : ''}
                                onChange={(e) => setCell(c.id, h.id, tn, Number(e.target.value))}
                              />
                            </td>
                          );
                        })}
                      </tr>
                    )),
                  )}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

// ===================================================================
// "Generate invoices" — confirmation flow
// ===================================================================

function GenerateButton({
  tenantId, versionId, terms, onSuccess, onError,
}: {
  tenantId: string;
  versionId: string;
  terms: TermDto[];
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [termNumber, setTermNumber] = useState<number | 'all'>('all');

  const mutation = useMutation({
    mutationFn: () =>
      feeStructureApi.generate(tenantId, versionId, {
        termNumber: termNumber === 'all' ? null : termNumber,
      }),
    onSuccess: (r) => {
      setOpen(false);
      onSuccess(
        `Generated ${r.invoicesCreated} invoice${r.invoicesCreated === 1 ? '' : 's'}` +
        (r.invoicesSkippedDuplicate > 0 ? ` (${r.invoicesSkippedDuplicate} already existed)` : ''),
      );
    },
    onError: (e: Error) => onError(e.message || 'Generation failed'),
  });

  const total = useMemo(() => terms.length, [terms]);

  return (
    <>
      <Button onClick={() => setOpen(true)}>
        <Send size={14} className="mr-1" /> Generate invoices
      </Button>
      {open && (
        <Modal open onClose={() => setOpen(false)} title="Generate invoices">
          <div className="space-y-3 text-sm">
            <p className="text-slate-600">
              This issues fee invoices for every student enrolled in a class that has a matrix row
              in this version. Re-running is safe — already-generated invoices are skipped.
            </p>
            {total > 0 && (
              <div>
                <label className="text-xs text-slate-500">Term</label>
                <select
                  className="block w-full border border-slate-200 rounded px-2 py-1 mt-0.5 text-sm"
                  value={String(termNumber)}
                  onChange={(e) => setTermNumber(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                >
                  <option value="all">All terms (+ annual)</option>
                  {terms.map((t) => (
                    <option key={t.termNumber} value={t.termNumber}>
                      Term {t.termNumber} — {t.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
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

// ===================================================================
// "Import CSV" — preview-then-commit flow
// ===================================================================

const CSV_TEMPLATE =
  'class_name,fee_head_name,term_number,amount_rupees,is_optional\n' +
  'Class 1,Tuition,1,5000,false\n' +
  'Class 1,Tuition,2,5000,false\n' +
  'Class 1,Transport,,12000,true\n';

function ImportCsvButton({
  tenantId, versionId, onSuccess, onError,
}: {
  tenantId: string;
  versionId: string;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportResult<MatrixImportRowResult> | null>(null);

  const dryRun = useMutation({
    mutationFn: (f: File) => feeStructureApi.importMatrix(tenantId, versionId, f, true),
    onSuccess: (r) => setPreview(r),
    onError: (e: Error) => onError(e.message || 'Preview failed'),
  });

  const commit = useMutation({
    mutationFn: () => feeStructureApi.importMatrix(tenantId, versionId, file!, false),
    onSuccess: (r) => {
      setOpen(false);
      setFile(null);
      setPreview(null);
      onSuccess(`Imported ${r.acceptedCount} matrix row${r.acceptedCount === 1 ? '' : 's'}`);
    },
    onError: (e: Error) => onError(e.message || 'Commit failed'),
  });

  function downloadTemplate() {
    const blob = new Blob([CSV_TEMPLATE], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fee-structure-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  function onPick(f: File | null) {
    setFile(f);
    setPreview(null);
    if (f) dryRun.mutate(f);
  }

  const canCommit = !!preview && preview.errorCount === 0 && preview.acceptedCount > 0;

  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}>
        <Upload size={14} className="mr-1" /> Import CSV
      </Button>
      {open && (
        <Modal open onClose={() => { setOpen(false); setFile(null); setPreview(null); }} title="Import matrix from CSV">
          <div className="space-y-3 text-sm">
            <p className="text-slate-600">
              CSV columns: <code className="text-xs">class_name, fee_head_name, term_number, amount_rupees, is_optional</code>.
              Leave <code className="text-xs">term_number</code> blank for annual billing.
              Class names and fee-head names must already exist in this tenant.
            </p>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={downloadTemplate}>
                <Download size={14} className="mr-1" /> Download template
              </Button>
              <label className="inline-flex items-center gap-2 text-sm cursor-pointer px-3 py-1.5 rounded border border-slate-200 hover:bg-slate-50">
                <Upload size={14} />
                <span>{file ? file.name : 'Choose CSV…'}</span>
                <input
                  type="file"
                  accept=".csv,text/csv"
                  className="hidden"
                  onChange={(e) => onPick(e.target.files?.[0] ?? null)}
                />
              </label>
            </div>

            {dryRun.isPending && <div className="text-slate-500">Parsing…</div>}

            {preview && (
              <div className="space-y-2">
                <div className="text-xs text-slate-600">
                  Read <b>{preview.totalRowsRead}</b> · valid <b className="text-emerald-600">{preview.acceptedCount}</b> · errors <b className="text-danger">{preview.errorCount}</b>
                </div>

                {preview.errorCount > 0 && (
                  <div className="border border-rose-200 bg-rose-50 rounded p-2 max-h-40 overflow-auto text-xs">
                    {preview.errors.slice(0, 50).map((e) => (
                      <div key={e.row} className="text-rose-700">
                        Row {e.row}: {e.message}
                      </div>
                    ))}
                  </div>
                )}

                {preview.acceptedCount > 0 && (
                  <div className="border border-slate-200 rounded max-h-48 overflow-auto">
                    <table className="w-full text-xs">
                      <thead className="bg-slate-50 text-slate-500">
                        <tr>
                          <th className="text-left px-2 py-1">#</th>
                          <th className="text-left px-2 py-1">Class</th>
                          <th className="text-left px-2 py-1">Head</th>
                          <th className="text-left px-2 py-1">Term</th>
                          <th className="text-right px-2 py-1">Amount</th>
                        </tr>
                      </thead>
                      <tbody>
                        {preview.accepted.slice(0, 100).map((r) => (
                          <tr key={r.rowNumber} className="border-t border-slate-100">
                            <td className="px-2 py-1 text-slate-500">{r.rowNumber}</td>
                            <td className="px-2 py-1">{r.className}</td>
                            <td className="px-2 py-1">{r.feeHeadName}</td>
                            <td className="px-2 py-1">{r.termNumber ?? 'Annual'}</td>
                            <td className="px-2 py-1 text-right">{formatINR(r.amountPaise)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded p-2">
                  Importing will <b>replace all existing rows</b> in this draft. Terms (start/end/due dates) are kept as-is.
                </p>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={() => { setOpen(false); setFile(null); setPreview(null); }}>Cancel</Button>
              <Button
                disabled={!canCommit || commit.isPending}
                onClick={() => commit.mutate()}
              >
                {commit.isPending
                  ? 'Importing…'
                  : preview ? `Import ${preview.acceptedCount} row${preview.acceptedCount === 1 ? '' : 's'}` : 'Import'}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}

// ===================================================================
// "New version" modal
// ===================================================================

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
  const [name, setName] = useState('');
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
    <Modal open onClose={onClose} title="New fee-structure version">
      <div className="space-y-3 text-sm">
        <div>
          <label className="text-xs text-slate-500">Academic year</label>
          <div className="mt-0.5 text-slate-700">{defaultYearName ?? '—'}</div>
        </div>
        <div>
          <label className="text-xs text-slate-500">Name</label>
          <input
            autoFocus
            className="block w-full border border-slate-200 rounded px-2 py-1.5 mt-0.5 text-sm"
            placeholder="2026–27 main"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div>
          <label className="text-xs text-slate-500">Notes (optional)</label>
          <textarea
            className="block w-full border border-slate-200 rounded px-2 py-1.5 mt-0.5 text-sm"
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!name.trim() || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? 'Creating…' : 'Create draft'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
