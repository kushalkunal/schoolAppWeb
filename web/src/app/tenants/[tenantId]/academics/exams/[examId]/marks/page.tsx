'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, Save, CheckCircle2 } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { MARKS_WRITER, RequireRole, useHasRole } from '@/auth/RequireRole';
import type { MarkEntryDto, SubjectResponse } from '@/types/domain';

/**
 * Marks-entry grid. Students on rows × subjects on columns. The teacher picks a section,
 * fills cells (or marks absent), then clicks Save Draft or Submit Final.
 *
 * Backend always upserts on (examId, studentId, subjectId), so re-saves are idempotent and
 * mid-grid refreshes won't lose work.
 */
export default function MarksEntryPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const examId = typeof params.examId === 'string' ? params.examId : '';

  const canWrite = useHasRole('SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER');

  // Section selector (default = first section the teacher can see)
  const [sectionId, setSectionId] = useState<string>('');
  const classes = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  // Default section once classes load
  useEffect(() => {
    if (!sectionId && classes.data && classes.data.length > 0) {
      const firstSection = classes.data.flatMap((c) => c.sections)[0];
      if (firstSection) setSectionId(firstSection.id);
    }
  }, [classes.data, sectionId]);

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Link href={`/tenants/${tenantId}/academics/exams`} className="text-slate-500 hover:text-slate-700">
          <ChevronLeft size={18} />
        </Link>
        <h1 className="text-2xl font-semibold">Marks entry</h1>
      </div>

      {classes.isLoading && <Spinner />}
      {classes.isError && <ErrorBanner error={classes.error} onRetry={() => classes.refetch()} />}

      {classes.data && (
        <Card>
          <label className="block">
            <span className="text-sm text-slate-700 mb-1 inline-block">Section</span>
            <select
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              className="block w-full rounded border border-slate-300 px-3 py-2 text-sm max-w-sm"
            >
              <option value="">Select a section</option>
              {classes.data.map((c) => (
                <optgroup key={c.id} label={c.name}>
                  {c.sections.map((s) => (
                    <option key={s.id} value={s.id}>{c.name} - {s.name}</option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
        </Card>
      )}

      {sectionId && tenantId && examId && (
        <MarksGrid tenantId={tenantId} examId={examId} sectionId={sectionId} canWrite={canWrite} />
      )}
    </div>
  );
}

function MarksGrid({ tenantId, examId, sectionId, canWrite }: {
  tenantId: string; examId: string; sectionId: string; canWrite: boolean;
}) {
  const qc = useQueryClient();

  const sheet = useQuery({
    queryKey: ['marks-sheet', tenantId, examId, sectionId],
    queryFn: () => academicsApi.getMarksSheet(tenantId, examId, sectionId),
  });
  const subjectsQ = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
  });
  const completion = useQuery({
    queryKey: ['marks-completion', tenantId, examId, sectionId],
    queryFn: () => academicsApi.getCompletionStatus(tenantId, examId, sectionId),
  });

  // Local draft state — keyed `${studentId}|${subjectId}` → { obtained, absent, max }
  const [draft, setDraft] = useState<Record<string, { obtained: string; absent: boolean; max: string }>>({});
  const [defaultMax, setDefaultMax] = useState<string>('100');

  // Seed from existing marks once they arrive
  useEffect(() => {
    if (!sheet.data) return;
    const seeded: typeof draft = {};
    for (const m of sheet.data.existingMarks) {
      seeded[`${m.studentId}|${m.subjectId}`] = {
        obtained: m.obtainedMarks != null ? String(m.obtainedMarks) : '',
        absent: m.absent,
        max: String(m.maxMarks),
      };
    }
    setDraft(seeded);
  }, [sheet.data]);

  const subjects = subjectsQ.data ?? [];

  const setCell = (studentId: string, subjectId: string, patch: Partial<{ obtained: string; absent: boolean; max: string }>) => {
    setDraft((d) => {
      const key = `${studentId}|${subjectId}`;
      const existing = d[key] ?? { obtained: '', absent: false, max: defaultMax };
      return { ...d, [key]: { ...existing, ...patch } };
    });
  };

  const buildEntries = (): MarkEntryDto[] => {
    const out: MarkEntryDto[] = [];
    if (!sheet.data) return out;
    for (const student of sheet.data.students) {
      for (const subj of subjects) {
        const key = `${student.studentId}|${subj.id}`;
        const cell = draft[key];
        if (!cell) continue;
        // Skip empty cells unless marked absent
        if (!cell.absent && cell.obtained === '') continue;
        const max = Number(cell.max || defaultMax);
        if (!Number.isFinite(max) || max <= 0) continue;
        out.push({
          studentId: student.studentId,
          subjectId: subj.id,
          maxMarks: max,
          obtainedMarks: cell.absent ? null : (cell.obtained === '' ? null : Number(cell.obtained)),
          absent: cell.absent,
        });
      }
    }
    return out;
  };

  const save = useMutation({
    mutationFn: (submitFinal: boolean) => academicsApi.submitMarks(tenantId, examId, {
      sectionId,
      entries: buildEntries(),
      submitFinal,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['marks-sheet', tenantId, examId, sectionId] });
      qc.invalidateQueries({ queryKey: ['marks-completion', tenantId, examId, sectionId] });
    },
  });

  const filledCount = useMemo(() => Object.values(draft).filter((c) => c.absent || c.obtained !== '').length, [draft]);

  if (sheet.isLoading || subjectsQ.isLoading) return <Spinner />;
  if (sheet.isError) return <ErrorBanner error={sheet.error} onRetry={() => sheet.refetch()} />;
  if (!sheet.data) return null;

  const students = sheet.data.students;
  if (subjects.length === 0) {
    return (
      <Card>
        <div className="text-center py-6 text-slate-600">
          <p className="font-medium mb-1">No subjects defined</p>
          <p className="text-sm text-slate-500">Add subjects via Settings → Academics before entering marks.</p>
        </div>
      </Card>
    );
  }
  if (students.length === 0) {
    return (
      <Card>
        <div className="text-center py-6 text-slate-500">No students enrolled in this section.</div>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between text-sm">
        <div className="flex items-center gap-4 text-slate-600">
          <span>{students.length} students × {subjects.length} subjects</span>
          <span>{filledCount} cells filled</span>
          {completion.data && (
            <span>
              Section completion: <span className="font-medium text-slate-800">{completion.data.percentComplete}%</span>
              <span className="text-slate-500"> ({completion.data.studentsWithAllMarks}/{completion.data.totalStudents})</span>
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs text-slate-600 flex items-center gap-1">
            Max marks
            <input
              type="number"
              value={defaultMax}
              min={1}
              onChange={(e) => setDefaultMax(e.target.value)}
              className="w-16 rounded border border-slate-300 px-2 py-1 text-xs"
            />
          </label>
          <RequireRole roles={MARKS_WRITER}>
            <Button variant="secondary" size="sm" onClick={() => save.mutate(false)} disabled={save.isPending || !canWrite}>
              <Save size={14} className="mr-1" /> Save draft
            </Button>
            <Button size="sm" onClick={() => {
              if (confirm('Submit final marks for this section? Cells become read-only.')) save.mutate(true);
            }} disabled={save.isPending || !canWrite}>
              <CheckCircle2 size={14} className="mr-1" /> Submit final
            </Button>
          </RequireRole>
        </div>
      </div>

      {save.isError && <ErrorBanner error={save.error} />}
      {save.isSuccess && !save.isPending && (
        <div className="bg-green-50 border border-green-200 text-green-800 text-sm rounded px-3 py-2">
          Saved {save.data?.length ?? 0} marks.
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-600 text-xs uppercase sticky top-0">
            <tr>
              <th className="text-left px-3 py-2 font-medium w-12">Roll</th>
              <th className="text-left px-3 py-2 font-medium min-w-[180px]">Student</th>
              {subjects.map((s) => (
                <th key={s.id} className="text-center px-2 py-2 font-medium min-w-[90px]">
                  {s.name}{s.code && <div className="text-[10px] font-normal text-slate-400">{s.code}</div>}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {students.map((st) => (
              <tr key={st.studentId} className="border-t border-slate-100 hover:bg-slate-50">
                <td className="px-3 py-2 text-slate-500">{st.rollNumber ?? '—'}</td>
                <td className="px-3 py-2">
                  <div className="font-medium text-slate-800">{st.displayName}</div>
                  <div className="text-xs text-slate-400">{st.admissionNumber}</div>
                </td>
                {subjects.map((subj) => (
                  <MarkCell
                    key={subj.id}
                    cell={draft[`${st.studentId}|${subj.id}`] ?? { obtained: '', absent: false, max: defaultMax }}
                    disabled={!canWrite}
                    onChange={(patch) => setCell(st.studentId, subj.id, patch)}
                  />
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function MarkCell({ cell, disabled, onChange }: {
  cell: { obtained: string; absent: boolean; max: string };
  disabled: boolean;
  onChange: (patch: Partial<{ obtained: string; absent: boolean; max: string }>) => void;
}) {
  return (
    <td className="px-2 py-1 text-center">
      {cell.absent ? (
        <button
          type="button"
          onClick={() => !disabled && onChange({ absent: false })}
          disabled={disabled}
          className="px-2 py-1 text-xs rounded bg-red-100 text-red-800 font-medium hover:bg-red-200 w-full"
        >
          ABSENT
        </button>
      ) : (
        <div className="flex items-center gap-1 justify-center">
          <input
            type="number"
            inputMode="numeric"
            value={cell.obtained}
            disabled={disabled}
            min={0}
            max={Number(cell.max) || undefined}
            onChange={(e) => onChange({ obtained: e.target.value })}
            className="w-14 rounded border border-slate-300 px-1 py-1 text-sm text-center"
            placeholder="—"
          />
          <button
            type="button"
            onClick={() => onChange({ absent: true, obtained: '' })}
            disabled={disabled}
            className="text-[10px] text-slate-400 hover:text-red-600"
            title="Mark absent"
          >
            A
          </button>
        </div>
      )}
    </td>
  );
}
