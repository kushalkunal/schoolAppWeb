'use client';

import { useMemo, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Upload, FileSpreadsheet, Users, UserCog, CheckCircle2,
  XCircle, Download, Sparkles, AlertCircle, ArrowRight,
} from 'lucide-react';
import { importsApi, type ImportResult } from '@/api/endpoints/imports';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Stat } from '@/components/ui/Stat';
import { EmptyState } from '@/components/ui/EmptyState';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';

/**
 * Bulk CSV import. Two-stage flow:
 *   1. Drop a file → auto dry-run → preview
 *   2. Click "Import N rows" → commit (dryRun=false)
 *
 * Templates are generated client-side; no backend round-trip needed.
 */

type ImportKind = 'students' | 'staff';

const TEMPLATES: Record<ImportKind, { headers: string[]; example: string[]; required: string[] }> = {
  students: {
    headers: [
      'first_name', 'last_name', 'class', 'section',
      'parent_phone', 'parent_name', 'parent_email', 'parent_relation',
      'admission_number', 'gender', 'date_of_birth', 'blood_group', 'address',
    ],
    example: [
      'Asha', 'Patil', 'Class 5', 'A',
      '9876543210', 'Ravi Patil', 'ravi.patil@gmail.com', 'FATHER',
      'ADM-2026-001', 'Female', '2015-04-12', 'O+', '45 MG Road Bangalore',
    ],
    required: ['first_name', 'class', 'section', 'parent_phone'],
  },
  staff: {
    headers: ['first_name', 'last_name', 'phone', 'email', 'role'],
    example: ['Geeta', 'Sharma', '9876543210', 'geeta@school.in', 'CLASS_TEACHER'],
    required: ['first_name', 'phone', 'role'],
  },
};

const KIND_META: Record<ImportKind, { label: string; icon: typeof Users; tone: 'primary' | 'accent' }> = {
  students: { label: 'Students', icon: Users,   tone: 'primary' },
  staff:    { label: 'Staff',    icon: UserCog, tone: 'accent' },
};

export default function ImportsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [kind, setKind] = useState<ImportKind>('students');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportResult<unknown> | null>(null);
  const [committed, setCommitted] = useState<ImportResult<unknown> | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // ---------------- Mutations ----------------
  const previewMut = useMutation({
    mutationFn: (f: File) => kind === 'students'
      ? importsApi.students(tenantId, f, true)
      : importsApi.staff(tenantId, f, true),
    onSuccess: (r) => {
      setPreview(r);
      setCommitted(null);
      toast.success(`Previewed ${r.totalRowsRead} rows`);
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not parse the file'),
  });

  const commitMut = useMutation({
    mutationFn: () => file
      ? (kind === 'students' ? importsApi.students(tenantId, file, false) : importsApi.staff(tenantId, file, false))
      : Promise.reject(new Error('No file selected')),
    onSuccess: (r) => {
      setCommitted(r);
      toast.success(`Imported ${r.acceptedCount} ${KIND_META[kind].label.toLowerCase()}`);
      // Invalidate dependent queries so list pages refresh on next visit.
      if (kind === 'students') qc.invalidateQueries({ queryKey: ['students', tenantId] });
      else qc.invalidateQueries({ queryKey: ['staff', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Import failed'),
  });

  // ---------------- File handling ----------------
  function handleFile(f: File) {
    if (!f.name.toLowerCase().endsWith('.csv')) {
      toast.error('Please upload a .csv file');
      return;
    }
    setFile(f);
    setPreview(null);
    setCommitted(null);
    previewMut.mutate(f);
  }

  function reset() {
    setFile(null);
    setPreview(null);
    setCommitted(null);
    if (inputRef.current) inputRef.current.value = '';
  }

  function downloadTemplate() {
    const t = TEMPLATES[kind];
    const csv = [t.headers.join(','), t.example.join(',')].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${kind}-template.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  // Feature-flag check via the actual call's error code
  const flagDisabled = previewMut.isError && hasCode(previewMut.error, 'FEATURE_DISABLED');

  return (
    <div className="space-y-5">
      <PageHeader
        title="Bulk import"
        description="Upload a CSV to onboard students or staff in one shot"
        icon={<FileSpreadsheet size={18} />}
      />

      {flagDisabled ? (
        <EmptyState
          icon={<FileSpreadsheet size={28} />}
          title="Bulk import isn't enabled for your plan"
          description="Onboard 800 students in 20 minutes instead of 3 weeks. Available on STARTER+."
        />
      ) : (
        <RequireRole roles={OWNER_OR_ADMIN}
          fallback={
            <EmptyState
              icon={<UserCog size={28} />}
              title="Admin access required"
              description="Bulk import is restricted to school owners and administrators."
            />
          }>
          <div className="space-y-5">
            {/* Type selector */}
            <div className="flex gap-2 p-1 bg-slate-100 rounded-brand w-fit">
              {(['students', 'staff'] as const).map((k) => {
                const Icon = KIND_META[k].icon;
                const active = kind === k;
                return (
                  <button
                    key={k}
                    onClick={() => { setKind(k); reset(); }}
                    className={cn(
                      'inline-flex items-center gap-1.5 px-4 py-1.5 rounded-[calc(var(--brand-radius)-2px)] text-sm font-medium transition',
                      active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
                    )}
                  >
                    <Icon size={14} /> {KIND_META[k].label}
                  </button>
                );
              })}
            </div>

            {/* Step 1: Download template */}
            {!file && (
              <Card padding="md">
                <div className="flex items-start gap-3">
                  <span className="w-9 h-9 rounded-brand bg-primary-soft text-primary grid place-items-center shrink-0">
                    <Download size={16} />
                  </span>
                  <div className="flex-1">
                    <CardTitle>Step 1 · Download the template</CardTitle>
                    <CardDescription className="mb-3">
                      A pre-filled CSV with the right column headers and one example row.
                      Required columns: <code className="text-xs bg-slate-100 px-1 py-0.5 rounded">
                        {TEMPLATES[kind].required.join(', ')}
                      </code>
                    </CardDescription>
                    <Button variant="secondary" onClick={downloadTemplate}>
                      <Download size={14} /> Download {kind}-template.csv
                    </Button>
                  </div>
                </div>
              </Card>
            )}

            {/* Step 2: Drop zone */}
            {!file && (
              <div
                onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                onDragLeave={() => setDragOver(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setDragOver(false);
                  const f = e.dataTransfer.files[0];
                  if (f) handleFile(f);
                }}
                onClick={() => inputRef.current?.click()}
                className={cn(
                  'rounded-brand border-2 border-dashed transition cursor-pointer p-10 text-center',
                  dragOver
                    ? 'border-primary bg-primary-soft/50'
                    : 'border-slate-300 bg-white hover:border-primary/60 hover:bg-slate-50/60',
                )}
              >
                <input ref={inputRef} type="file" accept=".csv" className="hidden"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) handleFile(f);
                  }} />
                <span className="w-14 h-14 rounded-full bg-primary-soft text-primary grid place-items-center mx-auto mb-3">
                  <Upload size={22} />
                </span>
                <p className="font-semibold text-slate-900">
                  Step 2 · Drop your CSV here, or click to browse
                </p>
                <p className="text-sm text-slate-500 mt-1">
                  Max 5,000 rows per file. Encoding: UTF-8.
                </p>
              </div>
            )}

            {/* Step 3: Preview */}
            {file && (
              <Card padding="md">
                <div className="flex items-center justify-between gap-3 mb-4">
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="w-10 h-10 rounded-brand bg-primary-soft text-primary grid place-items-center shrink-0">
                      <FileSpreadsheet size={18} />
                    </span>
                    <div className="min-w-0">
                      <div className="font-semibold text-slate-900 truncate">{file.name}</div>
                      <div className="text-xs text-slate-500">
                        {(file.size / 1024).toFixed(1)} kB
                        {previewMut.isPending && ' · parsing…'}
                        {preview && ` · ${preview.totalRowsRead} rows read`}
                      </div>
                    </div>
                  </div>
                  <Button variant="ghost" size="sm" onClick={reset}>Use a different file</Button>
                </div>

                {previewMut.isPending && (
                  <p className="text-sm text-slate-500">Validating each row…</p>
                )}

                {preview && !committed && <PreviewBlock result={preview} kind={kind}
                  onCommit={() => commitMut.mutate()} committing={commitMut.isPending} />}

                {committed && <CommitBlock result={committed} kind={kind} onReset={reset} />}
              </Card>
            )}

            {/* Help / notes */}
            {!file && (
              <Card padding="md" className="bg-slate-50/60">
                <div className="flex items-start gap-3">
                  <Sparkles size={16} className="text-primary mt-0.5 shrink-0" />
                  <div className="text-sm text-slate-600 space-y-1.5">
                    <p className="font-medium text-slate-900">Tips for a clean import</p>
                    <ul className="list-disc list-inside text-xs space-y-1 text-slate-600">
                      {kind === 'students' && (
                        <>
                          <li>Create your classes + sections first under <strong>Settings → Classes</strong> — the importer won't auto-create them.</li>
                          <li>Use the same parent phone for siblings — they'll auto-link to one parent.</li>
                          <li>Dates: <code>2015-04-12</code> or <code>12/04/2015</code>.</li>
                        </>
                      )}
                      {kind === 'staff' && (
                        <>
                          <li>Role must be one of: <code>PRINCIPAL · ADMIN · CLASS_TEACHER · SUBJECT_TEACHER · ACCOUNTANT · VIEWER</code>.</li>
                          <li>Phone must be WhatsApp-capable for invites to send.</li>
                        </>
                      )}
                      <li>Preview first; the system only writes when you click <strong>Import</strong>.</li>
                    </ul>
                  </div>
                </div>
              </Card>
            )}
          </div>
        </RequireRole>
      )}
    </div>
  );
}

// ============================================================
// Preview block — shown after dry-run, before commit
// ============================================================

function PreviewBlock({ result, kind, onCommit, committing }: {
  result: ImportResult<unknown>;
  kind: ImportKind;
  onCommit: () => void;
  committing: boolean;
}) {
  const hasErrors = result.errorCount > 0;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        <Stat label="Rows read"  value={result.totalRowsRead} icon={<FileSpreadsheet size={16} />} tone="info" />
        <Stat label="Valid"      value={result.acceptedCount} icon={<CheckCircle2 size={16} />} tone="success" />
        <Stat label="With errors" value={result.errorCount}   icon={<XCircle size={16} />}
              tone={hasErrors ? 'danger' : 'success'} />
      </div>

      {/* Errors */}
      {hasErrors && (
        <div className="rounded-brand border border-danger/30 bg-danger/5 p-3">
          <div className="flex items-center gap-2 mb-2 text-sm font-medium text-danger">
            <AlertCircle size={14} /> {result.errorCount} row{result.errorCount !== 1 ? 's' : ''} have problems
          </div>
          <ul className="space-y-1 text-xs max-h-48 overflow-y-auto">
            {result.errors.slice(0, 200).map((e, i) => (
              <li key={i} className="flex items-baseline gap-2">
                <Badge tone="danger" size="sm">Row {e.row}</Badge>
                <span className="text-slate-700">{e.message}</span>
              </li>
            ))}
            {result.errors.length > 200 && (
              <li className="text-slate-500 italic">
                … and {result.errors.length - 200} more
              </li>
            )}
          </ul>
          <p className="mt-2 text-xs text-slate-600">
            Fix these rows in your CSV and re-upload, or click Import to bring in only the valid rows.
          </p>
        </div>
      )}

      {/* Commit CTA */}
      <div className="flex items-center justify-between border-t border-slate-200 pt-4">
        <div className="text-sm text-slate-600">
          {result.acceptedCount === 0
            ? 'No valid rows — fix the errors above and try again.'
            : `Ready to import ${result.acceptedCount} ${kind === 'students' ? 'student' : 'staff member'}${result.acceptedCount !== 1 ? 's' : ''}.`}
        </div>
        <Button onClick={onCommit} disabled={result.acceptedCount === 0} loading={committing} glow>
          Import {result.acceptedCount} {kind === 'students' ? 'student' : 'staff'}
          {result.acceptedCount !== 1 ? 's' : ''} <ArrowRight size={14} />
        </Button>
      </div>
    </div>
  );
}

// ============================================================
// Commit block — shown after import completed
// ============================================================

function CommitBlock({ result, kind, onReset }: {
  result: ImportResult<unknown>;
  kind: ImportKind;
  onReset: () => void;
}) {
  const allGood = result.errorCount === 0;
  return (
    <div className="space-y-4">
      <div className={cn(
        'rounded-brand p-4 flex items-center gap-3',
        allGood ? 'bg-success/10 border border-success/20' : 'bg-warning/10 border border-warning/20',
      )}>
        <span className={cn(
          'w-10 h-10 grid place-items-center rounded-full shrink-0',
          allGood ? 'bg-success text-white' : 'bg-warning text-white',
        )}>
          <CheckCircle2 size={20} />
        </span>
        <div className="flex-1">
          <div className="font-semibold text-slate-900">
            {result.acceptedCount} {kind === 'students' ? 'students' : 'staff'} imported
          </div>
          <div className="text-sm text-slate-600">
            {allGood
              ? 'All rows succeeded.'
              : `${result.errorCount} row${result.errorCount !== 1 ? 's' : ''} skipped due to errors (see below).`}
          </div>
        </div>
      </div>

      {result.errors.length > 0 && (
        <div className="rounded-brand border border-slate-200 p-3 max-h-48 overflow-y-auto text-xs">
          <p className="font-medium text-slate-700 mb-1.5">Skipped rows:</p>
          <ul className="space-y-1">
            {result.errors.slice(0, 50).map((e, i) => (
              <li key={i} className="flex items-baseline gap-2">
                <Badge tone="danger" size="sm">Row {e.row}</Badge>
                <span className="text-slate-700">{e.message}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex justify-end">
        <Button variant="secondary" onClick={onReset}>Import another file</Button>
      </div>
    </div>
  );
}
