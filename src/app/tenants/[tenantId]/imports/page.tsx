'use client';

/**
 * Data Migration — one unified flow: pick WHAT to import, see its full column spec + template,
 * upload a CSV, preview (dry-run with per-row errors), then commit. All types share the same
 * mechanics; each declares its columns + template + import function.
 */

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import {
  UserPlus, Users, BookOpen, Layers, Wallet, Download, UploadCloud,
  ChevronRight, ChevronLeft, Check, AlertTriangle, FileSpreadsheet,
} from 'lucide-react';
import { importsApi, type ImportResult } from '@/api/endpoints/imports';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

type Col = { name: string; required?: boolean; note: string };
type ImportType = {
  key: string;
  label: string;
  icon: React.ElementType;
  blurb: string;
  prerequisite?: string;
  columns: Col[];
  example: string[];
  run: (tenantId: string, file: File, dryRun: boolean) => Promise<ImportResult<unknown>>;
};

const TYPES: ImportType[] = [
  {
    key: 'classes', label: 'Classes & sections', icon: Layers,
    blurb: 'Create your class structure. One class per row; sections separated by | or ,.',
    columns: [
      { name: 'class_name', required: true, note: 'e.g. "Class 5". (Alias: name)' },
      { name: 'sections', note: 'e.g. "A|B|C". Blank → a single section "A".' },
    ],
    example: ['Class 5', 'A|B'],
    run: (t, f, d) => importsApi.generic(t, 'classes', f, d),
  },
  {
    key: 'subjects', label: 'Subjects', icon: BookOpen,
    blurb: 'Create the subjects taught at the school. One subject per row.',
    columns: [
      { name: 'name', required: true, note: 'Subject name, e.g. "Mathematics".' },
      { name: 'code', note: 'Optional short code, e.g. "MATH".' },
    ],
    example: ['Mathematics', 'MATH'],
    run: (t, f, d) => importsApi.generic(t, 'subjects', f, d),
  },
  {
    key: 'staff', label: 'Teachers & staff', icon: Users,
    blurb: 'Add staff with their login role. They get access per their role.',
    columns: [
      { name: 'first_name', required: true, note: 'Given name.' },
      { name: 'last_name', note: 'Family name.' },
      { name: 'phone', required: true, note: 'WhatsApp-capable number (login identifier).' },
      { name: 'email', note: 'Email (optional).' },
      { name: 'role', required: true, note: 'PRINCIPAL | ADMIN | CLASS_TEACHER | SUBJECT_TEACHER | ACCOUNTANT | VIEWER' },
    ],
    example: ['Geeta', 'Sharma', '9876543210', 'geeta@school.in', 'CLASS_TEACHER'],
    run: (t, f, d) => importsApi.staff(t, f, d),
  },
  {
    key: 'students', label: 'Students & parents', icon: UserPlus,
    blurb: 'Add students with their parent. Siblings auto-link by parent phone.',
    prerequisite: 'Create the matching Classes & sections first — rows reference them by name.',
    columns: [
      { name: 'first_name', required: true, note: 'Given name.' },
      { name: 'last_name', note: 'Family name.' },
      { name: 'class', required: true, note: 'Must match an existing class name exactly.' },
      { name: 'section', required: true, note: 'Must match an existing section.' },
      { name: 'parent_phone', required: true, note: 'Used to auto-link siblings to one parent.' },
      { name: 'parent_name', note: 'Parent/guardian name.' },
      { name: 'parent_email', note: 'Parent email (for notifications).' },
      { name: 'parent_relation', note: 'FATHER | MOTHER | GUARDIAN' },
      { name: 'admission_number', note: 'Unique per school; prevents duplicates.' },
      { name: 'gender', note: 'e.g. Male / Female / Other.' },
      { name: 'date_of_birth', note: 'yyyy-MM-dd or dd/MM/yyyy.' },
      { name: 'blood_group', note: 'e.g. O+.' },
      { name: 'address', note: 'Home address.' },
    ],
    example: ['Asha', 'Patil', 'Class 5', 'A', '9876543210', 'Ravi Patil', 'ravi@gmail.com', 'FATHER', 'ADM-2026-001', 'Female', '2015-04-12', 'O+', '45 MG Road'],
    run: (t, f, d) => importsApi.students(t, f, d),
  },
  {
    key: 'opening-balances', label: 'Fee opening balances', icon: Wallet,
    blurb: 'Carry forward existing dues at go-live. One opening-balance invoice per row.',
    prerequisite: 'Import Students first — rows are matched by admission number.',
    columns: [
      { name: 'admission_number', required: true, note: 'Must match an imported student.' },
      { name: 'amount', required: true, note: 'Outstanding due in rupees, e.g. 1500 or 1500.00.' },
      { name: 'note', note: 'Optional description, e.g. "FY24-25 carry forward".' },
    ],
    example: ['ADM-2026-001', '1500', 'Carried from last year'],
    run: (t, f, d) => importsApi.generic(t, 'opening-balances', f, d),
  },
];

export default function ImportsPage() {
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [selected, setSelected] = useState<ImportType | null>(null);

  return (
    <div className="space-y-5">
      <PageHeader title="Data Migration" description="Bring your school's existing data in — one type at a time, via CSV." icon={<FileSpreadsheet />}
        actions={selected && <button onClick={() => setSelected(null)} className="text-sm text-slate-500 hover:underline inline-flex items-center gap-1"><ChevronLeft size={14} /> All imports</button>} />

      {!selected ? (
        <>
          <p className="text-sm text-slate-500">Choose what to import. Recommended order: Classes → Subjects → Teachers → Students → Fees.</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {TYPES.map((t, i) => (
              <button key={t.key} onClick={() => setSelected(t)}
                className="text-left rounded-xl border border-slate-200 bg-white p-4 hover:border-primary hover:shadow-sm transition">
                <div className="flex items-center gap-3">
                  <span className="grid h-9 w-9 place-items-center rounded-lg bg-primary-soft text-primary"><t.icon size={17} /></span>
                  <div className="min-w-0">
                    <p className="font-semibold text-slate-800">{i + 1}. {t.label}</p>
                    <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{t.blurb}</p>
                  </div>
                  <ChevronRight size={16} className="text-slate-300 ml-auto shrink-0" />
                </div>
              </button>
            ))}
          </div>
          <div className="rounded-lg bg-slate-50 border border-slate-200 px-4 py-3 text-sm text-slate-600">
            After importing classes, subjects, staff and students, set your fee amounts and generate invoices in{' '}
            <Link href={`/tenants/${tenantId}/fees/monthly`} className="text-primary font-medium hover:underline">Fees → Monthly</Link>.
            Use <b>Fee opening balances</b> above only to carry forward existing dues.
          </div>
        </>
      ) : (
        <ImportPanel key={selected.key} tenantId={tenantId} type={selected} />
      )}
    </div>
  );
}

function ImportPanel({ tenantId, type }: { tenantId: string; type: ImportType }) {
  const toast = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportResult<unknown> | null>(null);
  const [committed, setCommitted] = useState<ImportResult<unknown> | null>(null);

  const dryRun = useMutation({
    mutationFn: (f: File) => type.run(tenantId, f, true),
    onSuccess: (r) => { setPreview(r); setCommitted(null); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Could not read file'),
  });
  const commit = useMutation({
    mutationFn: (f: File) => type.run(tenantId, f, false),
    onSuccess: (r) => { setCommitted(r); setPreview(null); toast.success(`Imported ${r.acceptedCount} row(s)`); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Import failed'),
  });

  function downloadTemplate() {
    const header = type.columns.map((c) => c.name).join(',');
    const row = type.example.join(',');
    const blob = new Blob([`${header}\n${row}\n`], { type: 'text/csv' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `${type.key}-template.csv`; a.click();
  }
  function onPick(f: File | null) { setFile(f); setPreview(null); setCommitted(null); if (f) dryRun.mutate(f); }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 place-items-center rounded-lg bg-primary-soft text-primary"><type.icon size={18} /></span>
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{type.label}</h2>
          <p className="text-sm text-slate-500">{type.blurb}</p>
        </div>
      </div>

      {type.prerequisite && (
        <div className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-700 flex items-start gap-2">
          <AlertTriangle size={14} className="mt-0.5 shrink-0" /> {type.prerequisite}
        </div>
      )}

      <Card>
        <CardBody className="p-0 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr><th className="text-left px-4 py-2 font-medium">Column</th><th className="text-left px-4 py-2 font-medium">Required</th><th className="text-left px-4 py-2 font-medium">Notes</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {type.columns.map((c) => (
                <tr key={c.name}>
                  <td className="px-4 py-2 font-mono text-xs text-slate-800">{c.name}</td>
                  <td className="px-4 py-2">{c.required ? <span className="text-red-600 font-medium">Yes</span> : <span className="text-slate-400">—</span>}</td>
                  <td className="px-4 py-2 text-slate-600">{c.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardBody>
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onClick={downloadTemplate}><Download size={14} className="mr-1" /> Download template</Button>
        <label className="inline-flex items-center gap-1.5 rounded-brand border border-dashed border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 cursor-pointer">
          <UploadCloud size={15} /> {file ? file.name : 'Choose CSV'}
          <input type="file" accept=".csv" className="hidden" onChange={(e) => onPick(e.target.files?.[0] ?? null)} />
        </label>
        {dryRun.isPending && <span className="text-xs text-slate-400 inline-flex items-center gap-1"><Spinner /> Checking…</span>}
      </div>

      {preview && (
        <Card>
          <CardBody className="space-y-2 text-sm">
            <p><b>{preview.acceptedCount}</b> valid · <span className={preview.errorCount ? 'text-red-600' : 'text-slate-500'}>{preview.errorCount} error(s)</span> · {preview.duplicateCount} duplicate(s) · {preview.totalRowsRead} rows read</p>
            {preview.errors.length > 0 && (
              <ul className="max-h-48 overflow-y-auto text-xs text-red-600 space-y-0.5">
                {preview.errors.slice(0, 100).map((er, i) => <li key={i} className="flex gap-1.5"><AlertTriangle size={12} className="mt-0.5 shrink-0" />Row {er.row}: {er.message}</li>)}
                {preview.errors.length > 100 && <li className="text-slate-400">…{preview.errors.length - 100} more</li>}
              </ul>
            )}
            <Button size="sm" onClick={() => file && commit.mutate(file)} disabled={commit.isPending || preview.acceptedCount === 0}>
              {commit.isPending ? 'Importing…' : `Import ${preview.acceptedCount} row(s)`}
            </Button>
          </CardBody>
        </Card>
      )}

      {committed && (
        <div className="rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800 flex items-center gap-2">
          <Check size={16} /> Imported {committed.acceptedCount} row(s){committed.errorCount ? `, ${committed.errorCount} skipped` : ''}.
        </div>
      )}
    </div>
  );
}
