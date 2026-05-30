'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, Save, CheckCircle2, AlertCircle, BookOpen, ClipboardList, Lock, ShieldAlert } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { useHasRole } from '@/auth/RequireRole';
import type { BulkComponentMarksRequest, ComponentMarkEntryDto } from '@/types/domain';

/**
 * Component-aware marks entry grid.
 * Rows = students, columns = subject × component (Theory/70, Practical/30 …).
 * Auto-saves draft on blur; Submit Final triggers result computation.
 */
export default function MarksEntryPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const examId = typeof params.examId === 'string' ? params.examId : '';
  const canWrite = useHasRole('SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER');
  const isClassTeacher = useHasRole('CLASS_TEACHER');
  const isSubjectTeacher = useHasRole('SUBJECT_TEACHER');
  const isPrincipal = useHasRole('SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN');

  const [sectionId, setSectionId] = useState('');
  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  useEffect(() => {
    if (!sectionId && classesQ.data?.length) {
      const first = classesQ.data.flatMap(c => c.sections)[0];
      if (first) setSectionId(first.id);
    }
  }, [classesQ.data, sectionId]);

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Link href={`/tenants/${tenantId}/academics/exams`} className="text-slate-500 hover:text-slate-700">
          <ChevronLeft size={18} />
        </Link>
        <div>
          <h1 className="text-2xl font-semibold">Marks Entry</h1>
          <p className="text-sm text-slate-500">Enter component-wise marks. Submit Final locks marks and computes results.</p>
        </div>
      </div>

      {classesQ.isLoading && <Spinner />}
      {classesQ.isError && <ErrorBanner error={classesQ.error} />}

      {classesQ.data && (
        <Card>
          <label className="block">
            <span className="text-sm text-slate-700 mb-1 inline-block font-medium">Section</span>
            <select
              value={sectionId}
              onChange={e => setSectionId(e.target.value)}
              className="block w-full rounded border border-slate-300 px-3 py-2 text-sm max-w-sm"
            >
              <option value="">Select a section</option>
              {classesQ.data.map(c => (
                <optgroup key={c.id} label={c.name}>
                  {c.sections.map(s => (
                    <option key={s.id} value={s.id}>{c.name} — {s.name}</option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
        </Card>
      )}

      {sectionId && tenantId && examId && (
        <ComponentGrid
          tenantId={tenantId} examId={examId} sectionId={sectionId}
          canWrite={canWrite} isClassTeacher={isClassTeacher} isSubjectTeacher={isSubjectTeacher}
          isPrincipal={isPrincipal}
        />
      )}
    </div>
  );
}

type CellKey = string; // `${studentId}|${configId}`
type CellState = { obtained: string; absent: boolean };

function ComponentGrid({
  tenantId, examId, sectionId, canWrite, isClassTeacher, isSubjectTeacher, isPrincipal,
}: {
  tenantId: string; examId: string; sectionId: string; canWrite: boolean;
  isClassTeacher: boolean; isSubjectTeacher: boolean; isPrincipal: boolean;
}) {
  const qc = useQueryClient();
  const [cells, setCells] = useState<Record<CellKey, CellState>>({});
  const [dirty, setDirty] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const autoSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sheetQ = useQuery({
    queryKey: ['component-marks-sheet', tenantId, examId, sectionId],
    queryFn: () => academicsApi.getComponentMarksSheet(tenantId, examId, sectionId),
    staleTime: 0,
  });

  // Seed from server
  useEffect(() => {
    if (!sheetQ.data) return;
    const init: Record<CellKey, CellState> = {};
    for (const student of sheetQ.data.students) {
      for (const subject of student.subjects) {
        for (const comp of subject.components) {
          init[`${student.studentId}|${comp.configId}`] = {
            obtained: comp.obtained != null ? String(comp.obtained) : '',
            absent: comp.absent,
          };
        }
      }
    }
    setCells(init);
    setDirty(false);
  }, [sheetQ.data]);

  const saveMutation = useMutation({
    mutationFn: (req: BulkComponentMarksRequest) =>
      academicsApi.submitComponentMarks(tenantId, examId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['component-marks-sheet', tenantId, examId, sectionId] });
      setDirty(false);
      setSavedAt(new Date());
    },
  });

  function buildEntries(): ComponentMarkEntryDto[] {
    if (!sheetQ.data) return [];
    return sheetQ.data.students.flatMap(student =>
      student.subjects.flatMap(subj =>
        subj.components.map(comp => {
          const cell = cells[`${student.studentId}|${comp.configId}`];
          return {
            studentId: student.studentId,
            configId: comp.configId,
            obtained: cell?.absent ? 0 : (cell?.obtained ? parseFloat(cell.obtained) : undefined),
            absent: cell?.absent ?? false,
          };
        })
      )
    );
  }

  function save(submitFinal: boolean) {
    // Cancel any pending auto-save to prevent draft overwriting a final submit
    if (autoSaveTimer.current) {
      clearTimeout(autoSaveTimer.current);
      autoSaveTimer.current = null;
    }
    saveMutation.mutate({ sectionId, entries: buildEntries(), submitFinal });
  }

  function updateCell(key: CellKey, patch: Partial<CellState>) {
    setCells(prev => ({ ...prev, [key]: { obtained: '', absent: false, ...prev[key], ...patch } }));
    setDirty(true);
    if (autoSaveTimer.current) clearTimeout(autoSaveTimer.current);
    autoSaveTimer.current = setTimeout(() => { if (canWrite) save(false); }, 800);
  }

  if (sheetQ.isLoading) return <Spinner />;
  if (sheetQ.isError) return <ErrorBanner error={sheetQ.error} onRetry={() => sheetQ.refetch()} />;
  if (!sheetQ.data || !sheetQ.data.students.length)
    return <Card><p className="text-center text-slate-500 py-8">No students in this section.</p></Card>;

  // Section lock state
  const isLocked = sheetQ.data.locked;
  // true only if the logged-in CLASS_TEACHER is the class teacher of THIS specific section
  const effectiveIsClassTeacher = sheetQ.data.isOwnClassTeacher;
  // Principal/Admin can always edit even when locked; teachers cannot
  const effectiveCanWrite = canWrite && (!isLocked || isPrincipal);

  const firstStudent = sheetQ.data.students[0];
  if (!firstStudent || !firstStudent.subjects.length) {
    return (
      <Card>
        <p className="text-center text-slate-500 py-8">
          No exam structure configured.{' '}
          <Link href={`/tenants/${tenantId}/academics/exams/${examId}/structure`} className="text-indigo-600 underline">
            Configure the marking scheme first.
          </Link>
        </p>
      </Card>
    );
  }

  // Per-subject completion: a subject is "submitted" when ALL students' ALL components are not draft
  const subjectCompletion = firstStudent.subjects.map(subj => {
    const allFinal = sheetQ.data!.students.every(stu => {
      const s = stu.subjects.find(s => s.subjectId === subj.subjectId);
      return s?.components.every(c => !c.draft) ?? false;
    });
    const enteredCount = sheetQ.data!.students.filter(stu => {
      const s = stu.subjects.find(s => s.subjectId === subj.subjectId);
      return s?.components.some(c => c.obtained != null || c.absent) ?? false;
    }).length;
    return { subjectId: subj.subjectId, subjectName: subj.subjectName, allFinal, enteredCount };
  });
  const totalSubjects = subjectCompletion.length;
  const submittedSubjects = subjectCompletion.filter(s => s.allFinal).length;

  return (
    <div className="space-y-3">
      {/* LOCKED: shown to teachers when class teacher has submitted final */}
      {isLocked && !isPrincipal && (
        <div className="flex items-start gap-3 bg-slate-100 border border-slate-300 rounded-lg px-4 py-3">
          <Lock size={18} className="text-slate-500 mt-0.5 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-slate-700">
              Marks locked — submitted by {sheetQ.data.lockedByName ?? 'Class Teacher'}
            </p>
            <p className="text-xs text-slate-500 mt-0.5">
              {sheetQ.data.lockedAt
                ? `Locked on ${new Date(sheetQ.data.lockedAt).toLocaleString()}.`
                : ''}{' '}
              Contact the Principal if a correction is needed.
            </p>
          </div>
        </div>
      )}

      {/* LOCKED: override notice for Principal */}
      {isLocked && isPrincipal && (
        <div className="flex items-start gap-3 bg-amber-50 border border-amber-300 rounded-lg px-4 py-3">
          <ShieldAlert size={18} className="text-amber-600 mt-0.5 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-amber-800">
              Override mode — locked by {sheetQ.data.lockedByName ?? 'Class Teacher'}
            </p>
            <p className="text-xs text-amber-600 mt-0.5">
              As Principal, you can edit and re-submit. Results will be recomputed on save.
            </p>
          </div>
        </div>
      )}
      {/* Subject teacher / non-own-class-teacher context banner */}
      {!effectiveIsClassTeacher && !isPrincipal && firstStudent.subjects.length > 0 && (
        <div className="flex items-start gap-3 bg-indigo-50 border border-indigo-200 rounded-lg px-4 py-3">
          <BookOpen size={18} className="text-indigo-600 mt-0.5 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-indigo-800">
              Entering marks for: {firstStudent.subjects.map(s => s.subjectName).join(', ')}
            </p>
            <p className="text-xs text-indigo-600 mt-0.5">
              Only your assigned subject(s) are shown. Enter marks and click <strong>Submit Final</strong> when done.
            </p>
          </div>
        </div>
      )}

      {/* Class teacher completion status panel — only shown to the actual class teacher of this section */}
      {effectiveIsClassTeacher && totalSubjects > 0 && (
        <Card>
          <div className="flex items-center gap-2 mb-3">
            <ClipboardList size={16} className="text-slate-600" />
            <span className="font-semibold text-slate-800 text-sm">
              Subject Completion Status — {submittedSubjects}/{totalSubjects} submitted
            </span>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            {subjectCompletion.map(sc => (
              <div key={sc.subjectId}
                className={`rounded-lg border px-3 py-2 text-xs ${
                  sc.allFinal
                    ? 'border-green-200 bg-green-50'
                    : sc.enteredCount > 0
                      ? 'border-amber-200 bg-amber-50'
                      : 'border-slate-200 bg-slate-50'
                }`}>
                <p className={`font-medium truncate ${sc.allFinal ? 'text-green-800' : 'text-slate-700'}`}>
                  {sc.subjectName}
                </p>
                <p className={`mt-0.5 ${sc.allFinal ? 'text-green-600' : sc.enteredCount > 0 ? 'text-amber-600' : 'text-slate-400'}`}>
                  {sc.allFinal
                    ? '✓ Submitted'
                    : sc.enteredCount > 0
                      ? `${sc.enteredCount} entered (draft)`
                      : 'Pending'}
                </p>
              </div>
            ))}
          </div>
          {submittedSubjects < totalSubjects && (
            <p className="text-xs text-amber-700 mt-3 bg-amber-50 border border-amber-200 rounded px-3 py-2">
              {totalSubjects - submittedSubjects} subject(s) still have draft or no marks.
              Fill in any missing marks below, then click <strong>Submit All (Final)</strong> to lock and generate results.
            </p>
          )}
          {submittedSubjects === totalSubjects && (
            <p className="text-xs text-green-700 mt-3 bg-green-50 border border-green-200 rounded px-3 py-2">
              All subjects have been submitted. Results have been computed for this section.
            </p>
          )}
        </Card>
      )}

      {effectiveCanWrite && (
        <div className="flex items-center justify-between bg-white border border-slate-200 rounded-lg px-4 py-3">
          <div className="text-sm">
            {dirty && <span className="flex items-center gap-1 text-amber-600"><AlertCircle size={14} />Unsaved changes</span>}
            {savedAt && !dirty && <span className="flex items-center gap-1 text-green-600"><CheckCircle2 size={14} />Saved {savedAt.toLocaleTimeString()}</span>}
            {saveMutation.isPending && <span className="text-slate-400">Saving…</span>}
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => save(false)} disabled={saveMutation.isPending}>
              <Save size={14} className="mr-1" />Save Draft
            </Button>
            <Button size="sm" onClick={() => save(true)} disabled={saveMutation.isPending}>
              <CheckCircle2 size={14} className="mr-1" />
              {effectiveIsClassTeacher ? 'Submit All (Final)' : isPrincipal && isLocked ? 'Save & Recompute' : 'Submit Final'}
            </Button>
          </div>
        </div>
      )}
      {saveMutation.isError && <ErrorBanner error={saveMutation.error} />}

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="bg-slate-50">
              <th className="sticky left-0 bg-slate-50 text-left px-4 py-2 font-medium text-slate-700 border-b border-slate-200 min-w-[160px]">Student</th>
              {firstStudent.subjects.map(subj => (
                <th key={subj.subjectId}
                  colSpan={subj.components.length + 1}
                  className="text-center px-2 py-2 font-semibold text-slate-700 border-b border-l border-slate-200 text-xs whitespace-nowrap">
                  {subj.subjectName}
                </th>
              ))}
            </tr>
            <tr className="bg-slate-50 text-xs text-slate-500">
              <th className="sticky left-0 bg-slate-50 border-b border-slate-200 px-4 py-1" />
              {firstStudent.subjects.flatMap(subj => [
                ...subj.components.map(comp => (
                  <th key={comp.configId} className="text-center px-2 py-1 border-b border-l border-slate-200 whitespace-nowrap">
                    {comp.componentName}<br /><span className="text-slate-400">/{comp.maxMarks}</span>
                  </th>
                )),
                <th key={`${subj.subjectId}-ab`} className="text-center px-2 py-1 border-b border-l border-slate-200 text-red-400">Ab</th>,
              ])}
            </tr>
          </thead>
          <tbody>
            {sheetQ.data.students.map((student, ri) => (
              <tr key={student.studentId} className={ri % 2 === 0 ? 'bg-white' : 'bg-slate-50/40'}>
                <td className="sticky left-0 bg-inherit px-4 py-2 border-b border-slate-100">
                  <p className="font-medium text-slate-800 text-xs leading-tight">{student.name}</p>
                  {student.rollNumber != null && <p className="text-xs text-slate-400">Roll {student.rollNumber}</p>}
                </td>
                {student.subjects.flatMap(subj => [
                  ...subj.components.map(comp => {
                    const key = `${student.studentId}|${comp.configId}`;
                    const cell = cells[key] ?? { obtained: '', absent: false };
                    const val = parseFloat(cell.obtained);
                    const over = !isNaN(val) && val > comp.maxMarks;
                    return (
                      <td key={comp.configId} className="px-1.5 py-1 border-b border-l border-slate-100">
                        <input
                          type="number" min={0} max={comp.maxMarks} step={0.5}
                          disabled={!effectiveCanWrite || cell.absent}
                          value={cell.absent ? '' : cell.obtained}
                          onChange={e => updateCell(key, { obtained: e.target.value })}
                          placeholder={cell.absent ? 'Ab' : '—'}
                          className={[
                            'w-16 text-center border rounded px-1 py-0.5 text-xs focus:outline-none focus:ring-1',
                            over ? 'border-red-400 bg-red-50' : 'border-slate-200 focus:ring-indigo-300',
                            cell.absent ? 'bg-red-50 opacity-40' : '',
                          ].join(' ')}
                        />
                        {over && <p className="text-xs text-red-500">max {comp.maxMarks}</p>}
                      </td>
                    );
                  }),
                  <td key={`${subj.subjectId}-ab-${student.studentId}`} className="px-2 py-1 border-b border-l border-slate-100 text-center">
                    <input type="checkbox" disabled={!effectiveCanWrite}
                      checked={subj.components.every(c => cells[`${student.studentId}|${c.configId}`]?.absent ?? false)}
                      onChange={e => {
                        subj.components.forEach(c =>
                          updateCell(`${student.studentId}|${c.configId}`, { absent: e.target.checked })
                        );
                      }}
                      className="w-4 h-4 accent-red-500"
                    />
                  </td>,
                ])}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
