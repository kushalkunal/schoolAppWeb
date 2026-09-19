'use client';

/**
 * Simplified Exam Setup wizard. One guided flow:
 *   1. Session + exam details  2. Participating classes  3. Auto-enrollment preview
 *   4. Subjects & structure     5. Examination schedule    6. Generate admit cards → Done.
 *
 * Step 4 embeds the full component-based structure configuration (the same one as the standalone
 * Exam Structure page): per subject, define components (Theory / Practical / …) with max + passing
 * marks, with quick presets. Each step persists to the backend as it completes.
 */

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarCheck, Check, ChevronRight, Users, BookOpen, CalendarClock, IdCard, AlertTriangle, Plus, Trash2, CheckCircle2 } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { CreateExamRequest, ExamType, FeePolicy } from '@/types/domain';

const EXAM_TYPES: ExamType[] = ['UNIT_TEST', 'MID_TERM', 'TERM', 'FINAL_EXAM', 'ANNUAL', 'PRACTICAL', 'ASSESSMENT', 'INTERNAL', 'MOCK', 'ACTIVITY'];
const STEPS = ['Exam details', 'Classes', 'Students', 'Subjects & structure', 'Schedule', 'Admit cards'];

/** Quick presets — same as the standalone Exam Structure page. */
const PRESETS = [
  { label: 'Theory only (100)', components: [{ componentName: 'Theory', maxMarks: 100, passingMarks: 33, sortOrder: 1 }] },
  { label: 'Theory 80 + Practical 20', components: [{ componentName: 'Theory', maxMarks: 80, passingMarks: 26, sortOrder: 1 }, { componentName: 'Practical', maxMarks: 20, passingMarks: 7, sortOrder: 2 }] },
  { label: 'Theory 70 + Practical 30', components: [{ componentName: 'Theory', maxMarks: 70, passingMarks: 23, sortOrder: 1 }, { componentName: 'Practical', maxMarks: 30, passingMarks: 10, sortOrder: 2 }] },
  { label: 'Theory 50 + Internal 30 + Practical 20', components: [{ componentName: 'Theory', maxMarks: 50, passingMarks: 17, sortOrder: 1 }, { componentName: 'Internal', maxMarks: 30, passingMarks: 10, sortOrder: 2 }, { componentName: 'Practical', maxMarks: 20, passingMarks: 7, sortOrder: 3 }] },
] as const;

type ComponentDraft = { componentName: string; maxMarks: string; passingMarks: string; sortOrder: number };

export default function ExamSetupPage() {
  return <RequireRole roles={OWNER_OR_ADMIN}><Wizard /></RequireRole>;
}

function Wizard() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const toast = useToast();
  const qc = useQueryClient();

  const [step, setStep] = useState(0);
  const [examId, setExamId] = useState<string | null>(null);

  const yearsQ = useQuery({ queryKey: ['academic-years', tenantId], queryFn: () => academicsApi.listAcademicYears(tenantId), enabled: !!tenantId });
  const classesQ = useQuery({ queryKey: ['classes', tenantId], queryFn: () => schoolApi.listClasses(tenantId), enabled: !!tenantId });
  const subjectsQ = useQuery({ queryKey: ['subjects', tenantId], queryFn: () => academicsApi.listSubjects(tenantId), enabled: !!tenantId });

  // Step 1 — details (academicYearId defaults to the current session once loaded)
  const [form, setForm] = useState<CreateExamRequest>({ name: '', examType: 'TERM', startDate: '', endDate: '', feePolicy: 'BLOCK' });
  const years = yearsQ.data ?? [];
  useEffect(() => {
    if (!form.academicYearId && years.length) {
      setForm((f) => ({ ...f, academicYearId: (years.find((y) => y.current) ?? years[0])!.id }));
    }
  }, [years, form.academicYearId]);
  // Step 2 — classes
  const [classIds, setClassIds] = useState<string[]>([]);
  // Step 5 — schedule per subject {subjectId: {date, start, end}}
  const [sched, setSched] = useState<Record<string, { date: string; start: string; end: string }>>({});

  const classes = classesQ.data ?? [];
  const subjects = subjectsQ.data ?? [];

  // Exam structure (drives steps 4 & 5)
  const structureQ = useQuery({
    queryKey: ['exam-structure', tenantId, examId],
    queryFn: () => academicsApi.getExamStructure(tenantId, examId!),
    enabled: !!examId && step >= 3,
  });
  const structure = structureQ.data ?? [];
  const isConfigured = (subjectId: string) => structure.some((s) => s.subjectId === subjectId);

  const createExam = useMutation({
    mutationFn: () => academicsApi.createExam(tenantId, {
      ...form,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      classIds,
    }),
    onSuccess: (exam) => { setExamId(exam.id); toast.success('Exam created'); setStep(2); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to create exam'),
  });

  const saveClasses = useMutation({
    mutationFn: () => academicsApi.setParticipatingClasses(tenantId, examId!, classIds),
    onSuccess: () => { toast.success('Classes updated'); setStep(2); },
  });

  const saveSchedule = useMutation({
    mutationFn: () => academicsApi.saveExamSchedule(tenantId, examId!, {
      sittings: Object.entries(sched)
        .filter(([, v]) => v.date)
        .map(([subjectId, v]) => ({ subjectId, examDate: v.date, startTime: v.start || undefined, endTime: v.end || undefined })),
    }),
    onSuccess: () => { toast.success('Schedule saved'); setStep(5); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to save schedule'),
  });

  // ── Structure configure modal (component-based, mirrors the standalone page) ──
  const [cfgSubjectId, setCfgSubjectId] = useState('');
  const [cfgOpen, setCfgOpen] = useState(false);
  const [components, setComponents] = useState<ComponentDraft[]>([{ componentName: 'Theory', maxMarks: '100', passingMarks: '33', sortOrder: 1 }]);

  const openConfigure = (subjectId: string) => {
    setCfgSubjectId(subjectId);
    const existing = structure.find((s) => s.subjectId === subjectId);
    setComponents(existing
      ? existing.components.map((c) => ({ componentName: c.componentName, maxMarks: String(c.maxMarks), passingMarks: c.passingMarks != null ? String(c.passingMarks) : '', sortOrder: c.sortOrder }))
      : [{ componentName: 'Theory', maxMarks: '100', passingMarks: '33', sortOrder: 1 }]);
    setCfgOpen(true);
  };
  const applyPreset = (p: typeof PRESETS[number]) =>
    setComponents(p.components.map((c) => ({ componentName: c.componentName, maxMarks: String(c.maxMarks), passingMarks: String(c.passingMarks), sortOrder: c.sortOrder })));
  const addComponent = () => setComponents((prev) => [...prev, { componentName: '', maxMarks: '', passingMarks: '', sortOrder: prev.length + 1 }]);
  const removeComponent = (i: number) => setComponents((prev) => prev.filter((_, idx) => idx !== i).map((c, idx) => ({ ...c, sortOrder: idx + 1 })));
  const updateComponent = (i: number, field: keyof ComponentDraft, value: string) =>
    setComponents((prev) => prev.map((c, idx) => idx === i ? { ...c, [field]: value } : c));
  const cfgTotal = components.reduce((s, c) => s + (parseFloat(c.maxMarks) || 0), 0);

  const configure = useMutation({
    mutationFn: () => academicsApi.configureExamStructure(tenantId, examId!, {
      subjectId: cfgSubjectId,
      components: components.map((c, i) => ({
        componentName: c.componentName.trim(),
        maxMarks: parseFloat(c.maxMarks),
        passingMarks: c.passingMarks ? parseFloat(c.passingMarks) : undefined,
        sortOrder: i + 1,
      })).filter((c) => c.componentName && c.maxMarks > 0),
    }),
    onSuccess: () => { setCfgOpen(false); toast.success('Structure saved'); qc.invalidateQueries({ queryKey: ['exam-structure', tenantId, examId] }); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to save structure'),
  });

  // Step 3/6 data
  const enrollQ = useQuery({
    queryKey: ['exam-enroll', tenantId, examId], enabled: !!examId && step === 2,
    queryFn: () => academicsApi.getEnrollmentSummary(tenantId, examId!),
  });
  const validateQ = useQuery({
    queryKey: ['exam-validate', tenantId, examId], enabled: !!examId && step === 5,
    queryFn: () => academicsApi.validateAdmitCards(tenantId, examId!),
  });

  const generate = useMutation({
    mutationFn: () => academicsApi.bulkGenerateAdmitCards(tenantId, examId!),
    onSuccess: (r) => toast.success(`Generated ${r.generated} admit card(s)`),
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Generation failed'),
  });

  const toggleClass = (id: string) =>
    setClassIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

  const configuredSubjects = useMemo(() => subjects.filter((s) => isConfigured(s.id)), [subjects, structure]);
  const cfgSubjectName = subjects.find((s) => s.id === cfgSubjectId)?.name ?? '';

  if (yearsQ.isLoading || classesQ.isLoading) return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>;

  return (
    <div className="space-y-5">
      <PageHeader title="Exam Setup" description="Create an exam, pick classes, set subjects and schedule, then generate admit cards." icon={<CalendarCheck />}
        actions={<Link href={`/tenants/${tenantId}/academics/exams`} className="text-sm text-slate-500 hover:underline">All exams →</Link>} />

      {/* Stepper */}
      <ol className="flex flex-wrap gap-1 text-xs">
        {STEPS.map((label, i) => (
          <li key={label} className={`flex items-center gap-1 rounded-full px-3 py-1 ${i === step ? 'bg-primary text-white' : i < step ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-400'}`}>
            {i < step ? <Check size={12} /> : <span>{i + 1}.</span>} {label}
          </li>
        ))}
      </ol>

      {/* Step 1 — details */}
      {step === 0 && (
        <Card>
          <CardHeader><CardTitle className="text-base">Session &amp; exam details</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            <label className="block text-sm"><span className="mb-1 inline-block text-slate-700">Academic session</span>
              <select value={form.academicYearId ?? ''} onChange={(e) => setForm({ ...form, academicYearId: e.target.value })}
                className="block w-full rounded border border-slate-300 px-3 py-2 text-sm">
                {years.map((y) => <option key={y.id} value={y.id}>{y.name}{y.current ? ' (current)' : ''}</option>)}
                {years.length === 0 && <option value="">No session configured</option>}
              </select></label>
            <Input label="Exam name" value={form.name} required maxLength={100} placeholder="Mid-Term Examination"
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <label className="block text-sm"><span className="mb-1 inline-block text-slate-700">Type</span>
                <select value={form.examType} onChange={(e) => setForm({ ...form, examType: e.target.value as ExamType })}
                  className="block w-full rounded border border-slate-300 px-3 py-2 text-sm">
                  {EXAM_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
                </select></label>
              <Input label="Start date" type="date" value={form.startDate ?? ''} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
              <Input label="End date" type="date" value={form.endDate ?? ''} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
            </div>
            <label className="block text-sm"><span className="mb-1 inline-block text-slate-700">Admit-card fee policy</span>
              <select value={form.feePolicy} onChange={(e) => setForm({ ...form, feePolicy: e.target.value as FeePolicy })}
                className="block w-full rounded border border-slate-300 px-3 py-2 text-sm">
                <option value="BLOCK">Block if fee dues exist</option>
                <option value="ALLOW">Allow regardless of dues</option>
                <option value="OVERRIDE">Block, but allow admin override per student</option>
              </select></label>
            <div className="flex justify-end">
              <Button onClick={() => setStep(1)} disabled={!form.name}>
                Next: select classes <ChevronRight size={15} className="ml-1" />
              </Button>
            </div>
            <p className="text-xs text-slate-400">You&apos;ll pick participating classes on the next step — the exam is created then.</p>
          </CardBody>
        </Card>
      )}

      {/* Step 2 — classes */}
      {step === 1 && (
        <Card>
          <CardHeader><CardTitle className="text-base flex items-center gap-2"><Users size={16} /> Participating classes</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
              {classes.map((c) => (
                <label key={c.id} className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm cursor-pointer ${classIds.includes(c.id) ? 'border-primary bg-primary/5' : 'border-slate-200'}`}>
                  <input type="checkbox" checked={classIds.includes(c.id)} onChange={() => toggleClass(c.id)} />
                  {c.name}
                </label>
              ))}
            </div>
            {(createExam.isError || saveClasses.isError) && <ErrorBanner error={createExam.error ?? saveClasses.error} />}
            <div className="flex justify-between">
              <Button variant="secondary" onClick={() => setStep(0)}>Back</Button>
              <Button onClick={() => examId ? saveClasses.mutate() : createExam.mutate()} disabled={classIds.length === 0 || saveClasses.isPending || createExam.isPending}>
                {(saveClasses.isPending || createExam.isPending) ? 'Saving…' : <>Next <ChevronRight size={15} className="ml-1" /></>}
              </Button>
            </div>
          </CardBody>
        </Card>
      )}

      {/* Step 3 — auto-enrollment */}
      {step === 2 && (
        <Card>
          <CardHeader><CardTitle className="text-base flex items-center gap-2"><Users size={16} /> Auto-enrolled students</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            {enrollQ.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner /> Counting…</div>}
            {enrollQ.data && (
              <>
                <div className="rounded-lg bg-emerald-50 px-4 py-3 text-emerald-800">
                  <span className="text-2xl font-semibold">{enrollQ.data.totalStudents}</span> active students will be enrolled automatically.
                </div>
                <ul className="divide-y divide-slate-100 text-sm">
                  {enrollQ.data.classes.map((c) => (
                    <li key={c.classId} className="flex justify-between py-2"><span>{c.className}</span><span className="text-slate-500">{c.studentCount} students</span></li>
                  ))}
                </ul>
              </>
            )}
            <div className="flex justify-between">
              <Button variant="secondary" onClick={() => setStep(1)}>Back</Button>
              <Button onClick={() => setStep(3)}>Next: subjects &amp; structure <ChevronRight size={15} className="ml-1" /></Button>
            </div>
          </CardBody>
        </Card>
      )}

      {/* Step 4 — subjects & structure (component-based) */}
      {step === 3 && (
        <Card>
          <CardHeader><CardTitle className="text-base flex items-center gap-2"><BookOpen size={16} /> Subjects &amp; structure</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            <p className="text-sm text-slate-500">Configure the components (Theory, Practical, …) and marks for each subject.</p>
            {subjects.length === 0 ? (
              <p className="text-sm text-slate-500">No subjects yet. Add them in <Link href={`/tenants/${tenantId}/academics/subjects`} className="text-primary underline">Academics → Subjects</Link> first.</p>
            ) : (
              <div className="space-y-2">
                {subjects.map((s) => {
                  const existing = structure.find((x) => x.subjectId === s.id);
                  return (
                    <div key={s.id} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2.5">
                      <div className="flex items-center gap-3 min-w-0">
                        <BookOpen size={16} className="text-slate-400 shrink-0" />
                        <div className="min-w-0">
                          <p className="font-medium text-slate-800">{s.name}</p>
                          {existing ? (
                            <p className="text-xs text-slate-500 mt-0.5 truncate">
                              {existing.components.map((c) => `${c.componentName} (${c.maxMarks})`).join(' + ')} <span className="font-medium">= {existing.totalMax}</span>
                            </p>
                          ) : <p className="text-xs text-amber-600 mt-0.5">Not configured</p>}
                        </div>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        {existing && <CheckCircle2 size={16} className="text-green-500" />}
                        <Button variant="secondary" size="sm" onClick={() => openConfigure(s.id)}>{existing ? 'Edit' : 'Configure'}</Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
            <div className="flex justify-between">
              <Button variant="secondary" onClick={() => setStep(2)}>Back</Button>
              <Button onClick={() => setStep(4)} disabled={configuredSubjects.length === 0}>
                Next: schedule <ChevronRight size={15} className="ml-1" />
              </Button>
            </div>
            {configuredSubjects.length === 0 && <p className="text-xs text-slate-400">Configure at least one subject to continue.</p>}
          </CardBody>
        </Card>
      )}

      {/* Step 5 — schedule */}
      {step === 4 && (
        <Card>
          <CardHeader><CardTitle className="text-base flex items-center gap-2"><CalendarClock size={16} /> Examination schedule</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            {configuredSubjects.length === 0 ? (
              <p className="text-sm text-slate-500">No subjects configured. Go back and configure at least one subject.</p>
            ) : (
              <table className="w-full text-sm">
                <thead><tr className="text-xs uppercase text-slate-400 text-left"><th className="py-2">Subject</th><th className="py-2">Date</th><th className="py-2">Start</th><th className="py-2">End</th></tr></thead>
                <tbody className="divide-y divide-slate-50">
                  {configuredSubjects.map((s) => {
                    const v = sched[s.id] ?? { date: '', start: '', end: '' };
                    return (
                      <tr key={s.id}>
                        <td className="py-1.5 font-medium">{s.name}</td>
                        <td className="py-1.5"><input type="date" className="rounded border border-slate-200 px-2 py-1" value={v.date}
                          onChange={(e) => setSched({ ...sched, [s.id]: { ...v, date: e.target.value } })} /></td>
                        <td className="py-1.5"><input type="time" className="rounded border border-slate-200 px-2 py-1" value={v.start}
                          onChange={(e) => setSched({ ...sched, [s.id]: { ...v, start: e.target.value } })} /></td>
                        <td className="py-1.5"><input type="time" className="rounded border border-slate-200 px-2 py-1" value={v.end}
                          onChange={(e) => setSched({ ...sched, [s.id]: { ...v, end: e.target.value } })} /></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
            {saveSchedule.isError && <ErrorBanner error={saveSchedule.error} />}
            <div className="flex justify-between">
              <Button variant="secondary" onClick={() => setStep(3)}>Back</Button>
              <Button onClick={() => saveSchedule.mutate()} disabled={saveSchedule.isPending}>
                {saveSchedule.isPending ? 'Saving…' : <>Next: admit cards <ChevronRight size={15} className="ml-1" /></>}
              </Button>
            </div>
          </CardBody>
        </Card>
      )}

      {/* Step 6 — admit cards */}
      {step === 5 && (
        <Card>
          <CardHeader><CardTitle className="text-base flex items-center gap-2"><IdCard size={16} /> Generate admit cards</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            {validateQ.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner /> Validating…</div>}
            {validateQ.data && (
              <>
                {validateQ.data.warnings.length > 0 && (
                  <ul className="space-y-1 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-700">
                    {validateQ.data.warnings.map((w, i) => <li key={i} className="flex items-start gap-2"><AlertTriangle size={14} className="mt-0.5 shrink-0" />{w}</li>)}
                  </ul>
                )}
                <p className="text-sm text-slate-600">{validateQ.data.activeStudents} students are ready for admit cards. Cards auto-generate 3–5 days before the exam, or generate now:</p>
                <div className="flex items-center gap-3">
                  <Button onClick={() => generate.mutate()} disabled={!validateQ.data.ready || generate.isPending}>
                    {generate.isPending ? 'Generating…' : 'Generate admit cards now'}
                  </Button>
                  <Link href={`/tenants/${tenantId}/academics/exams/${examId}/admit-cards`} className="text-sm text-primary hover:underline">
                    Open admit-card dashboard →
                  </Link>
                </div>
                {generate.isSuccess && (
                  <div className="rounded-lg bg-emerald-50 px-4 py-3 text-emerald-800 flex items-center gap-2"><Check size={16} /> Done — admit cards generated. Preview / download / send from the dashboard.</div>
                )}
              </>
            )}
            <div className="flex justify-between pt-2">
              <Button variant="secondary" onClick={() => setStep(4)}>Back</Button>
              <Link href={`/tenants/${tenantId}/academics/exams`}><Button variant="secondary">Finish</Button></Link>
            </div>
          </CardBody>
        </Card>
      )}

      {/* Structure configure modal */}
      <Modal open={cfgOpen} onClose={() => setCfgOpen(false)} title={`Configure: ${cfgSubjectName}`}>
        <div className="space-y-4">
          <div>
            <p className="text-xs font-medium text-slate-600 mb-2">Quick presets</p>
            <div className="flex flex-wrap gap-2">
              {PRESETS.map((p) => (
                <button key={p.label} onClick={() => applyPreset(p)}
                  className="text-xs px-3 py-1.5 rounded-full border border-indigo-200 text-indigo-700 hover:bg-indigo-50 transition-colors">{p.label}</button>
              ))}
            </div>
          </div>
          <div>
            <div className="grid grid-cols-[1fr_110px_110px_32px] gap-2 text-xs font-medium text-slate-500 mb-1 px-1">
              <span>Component</span><span>Max marks</span><span>Passing marks</span><span />
            </div>
            {components.map((c, i) => (
              <div key={i} className="grid grid-cols-[1fr_110px_110px_32px] gap-2 mb-2">
                <Input value={c.componentName} placeholder="e.g. Theory" onChange={(e) => updateComponent(i, 'componentName', e.target.value)} />
                <Input type="number" value={c.maxMarks} placeholder="100" min={0} onChange={(e) => updateComponent(i, 'maxMarks', e.target.value)} />
                <Input type="number" value={c.passingMarks} placeholder="33" min={0} onChange={(e) => updateComponent(i, 'passingMarks', e.target.value)} />
                <button onClick={() => removeComponent(i)} disabled={components.length === 1}
                  className="text-slate-400 hover:text-red-500 disabled:opacity-30 transition-colors"><Trash2 size={15} /></button>
              </div>
            ))}
            <button onClick={addComponent} className="flex items-center gap-1 text-sm text-indigo-600 hover:text-indigo-800 mt-1"><Plus size={14} /> Add component</button>
          </div>
          <div className="flex items-center justify-between pt-2 border-t border-slate-100">
            <p className="text-sm font-medium text-slate-700">Total: {cfgTotal} marks</p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setCfgOpen(false)}>Cancel</Button>
              <Button onClick={() => configure.mutate()} disabled={configure.isPending || components.some((c) => !c.componentName || !c.maxMarks)}>
                {configure.isPending ? 'Saving…' : 'Save structure'}
              </Button>
            </div>
          </div>
          {configure.isError && <ErrorBanner error={configure.error} />}
        </div>
      </Modal>
    </div>
  );
}
