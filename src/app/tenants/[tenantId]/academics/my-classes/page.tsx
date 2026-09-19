'use client';

/**
 * Teacher's Academics landing — "My Classes".
 *
 * Auto-derives the (section × subject) pairs the teacher actually teaches from their timetable
 * (the same source as "My Weekly Schedule"), then lets them pick an exam and jump straight into
 * marks entry for that section. No hunting through the full section list.
 */

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { GraduationCap, BookOpen, ChevronRight, ClipboardList } from 'lucide-react';
import { timetableApi } from '@/api/endpoints/timetable';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { useAuth } from '@/auth/AuthProvider';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';

export default function MyClassesPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const { state } = useAuth();
  const staffId = state.status === 'authenticated' ? state.claims.sub : '';

  const timetableQ = useQuery({
    queryKey: ['teacher-timetable', tenantId, staffId],
    queryFn: () => timetableApi.getTeacherTimetable(tenantId, staffId),
    enabled: !!tenantId && !!staffId,
  });
  const classesQ = useQuery({ queryKey: ['classes', tenantId], queryFn: () => schoolApi.listClasses(tenantId), enabled: !!tenantId });
  const subjectsQ = useQuery({ queryKey: ['subjects', tenantId], queryFn: () => academicsApi.listSubjects(tenantId), enabled: !!tenantId });
  const examsQ = useQuery({ queryKey: ['exams', tenantId], queryFn: () => academicsApi.listExams(tenantId), enabled: !!tenantId });

  const [examId, setExamId] = useState('');
  const exams = examsQ.data ?? [];
  useEffect(() => { if (!examId && exams.length) setExamId(exams[0]!.id); }, [exams, examId]);

  // sectionId -> "Class X – Y" label, and subjectId -> name
  const sectionLabel = useMemo(() => {
    const m = new Map<string, string>();
    for (const c of classesQ.data ?? []) for (const s of c.sections) m.set(s.id, `${c.name} – ${s.name}`);
    return m;
  }, [classesQ.data]);
  const subjectName = useMemo(() => {
    const m = new Map<string, string>();
    for (const s of subjectsQ.data ?? []) m.set(s.id, s.name);
    return m;
  }, [subjectsQ.data]);

  // Distinct (section, subject) pairs the teacher teaches, from their timetable.
  const teaching = useMemo(() => {
    const seen = new Map<string, { sectionId: string; subjectId: string | null }>();
    for (const e of timetableQ.data ?? []) {
      const key = `${e.sectionId}|${e.subjectId ?? ''}`;
      if (!seen.has(key)) seen.set(key, { sectionId: e.sectionId, subjectId: e.subjectId });
    }
    return [...seen.values()].sort((a, b) =>
      (sectionLabel.get(a.sectionId) ?? '').localeCompare(sectionLabel.get(b.sectionId) ?? ''));
  }, [timetableQ.data, sectionLabel]);

  const loading = timetableQ.isLoading || classesQ.isLoading || subjectsQ.isLoading;

  return (
    <div className="space-y-5">
      <PageHeader title="My Classes" description="The classes and subjects you teach. Pick an exam and enter marks." icon={<GraduationCap />} />

      {/* Exam selector */}
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm"><span className="mb-1 block text-xs text-slate-500">Exam</span>
          <select className="rounded-lg border border-slate-200 px-3 py-2 text-sm min-w-[220px]" value={examId} onChange={(e) => setExamId(e.target.value)}>
            {exams.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
            {exams.length === 0 && <option value="">No exams yet</option>}
          </select>
        </label>
        {exams.length === 0 && <span className="text-xs text-slate-400">Ask your admin to create an exam first.</span>}
      </div>

      {loading && <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading your classes…</div>}
      {timetableQ.isError && <ErrorBanner error={timetableQ.error} onRetry={() => timetableQ.refetch()} />}

      {!loading && teaching.length === 0 && (
        <Card><CardBody className="py-10 text-center text-slate-500">
          <BookOpen className="mx-auto mb-2 opacity-40" size={28} />
          <p>No classes assigned to you yet.</p>
          <p className="text-xs text-slate-400 mt-1">Your timetable allocations will appear here once the admin sets them up.</p>
        </CardBody></Card>
      )}

      {!loading && teaching.length > 0 && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {teaching.map((t) => (
            <Card key={`${t.sectionId}|${t.subjectId}`}>
              <CardBody className="space-y-3">
                <div className="flex items-center gap-2">
                  <span className="grid h-9 w-9 place-items-center rounded-lg bg-primary-soft text-primary"><BookOpen size={16} /></span>
                  <div className="min-w-0">
                    <p className="font-semibold text-slate-800 truncate">{sectionLabel.get(t.sectionId) ?? 'Section'}</p>
                    <p className="text-xs text-slate-500 truncate">{t.subjectId ? (subjectName.get(t.subjectId) ?? 'Subject') : 'Class teacher'}</p>
                  </div>
                </div>
                {examId ? (
                  <Link
                    href={`/tenants/${tenantId}/academics/exams/${examId}/marks?sectionId=${t.sectionId}`}
                    className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
                  >
                    <ClipboardList size={14} /> Enter marks <ChevronRight size={14} />
                  </Link>
                ) : (
                  <span className="text-xs text-slate-400">Select an exam above to enter marks.</span>
                )}
              </CardBody>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
