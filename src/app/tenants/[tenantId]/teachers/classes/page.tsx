'use client';

/**
 * Teachers → Classes tab
 * Two sections on one page:
 *   1. Homeroom / class teacher per section
 *   2. Subject-teacher assignments (who teaches what in which class)
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { BookOpen, GraduationCap, Plus, Trash2, UserCog } from 'lucide-react';
import { schoolApi } from '@/api/endpoints/school';
import { academicsApi } from '@/api/endpoints/academics';
import { teacherAssignmentsApi } from '@/api/endpoints/teacherAssignments';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type {
  ClassResponse,
  SectionResponse,
  StaffResponse,
  SubjectResponse,
  TeacherAssignmentResponse,
} from '@/types/domain';

export default function TeacherClassesPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [assignOpen, setAssignOpen] = useState(false);

  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const subjectsQ = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const assignmentsQ = useQuery({
    queryKey: ['teacher-assignments', tenantId],
    queryFn: () => teacherAssignmentsApi.list(tenantId),
    enabled: !!tenantId,
  });

  const classTeachers = staffQ.data?.filter(
    (s) => s.active && s.role === 'CLASS_TEACHER',
  ) ?? [];
  const allTeachers = staffQ.data?.filter(
    (s) => s.active && (s.role === 'CLASS_TEACHER' || s.role === 'SUBJECT_TEACHER'),
  ) ?? [];

  const staffById = new Map<string, StaffResponse>(
    staffQ.data?.map((s) => [s.id, s]) ?? [],
  );
  const subjectById = new Map<string, SubjectResponse>(
    subjectsQ.data?.map((s) => [s.id, s]) ?? [],
  );
  const sectionLabel = new Map<string, string>();
  classesQ.data?.forEach((c: ClassResponse) => {
    c.sections.forEach((s: SectionResponse) => {
      sectionLabel.set(s.id, `${c.name} – ${s.name}`);
    });
  });

  // Map teacher → list of section labels where they're already class teacher
  // Used to warn when a teacher is assigned to multiple sections
  const teacherToSections = new Map<string, string[]>();
  classesQ.data?.forEach((c: ClassResponse) => {
    c.sections.forEach((s: SectionResponse) => {
      if (s.classTeacherId) {
        const list = teacherToSections.get(s.classTeacherId) ?? [];
        list.push(`${c.name} – ${s.name}`);
        teacherToSections.set(s.classTeacherId, list);
      }
    });
  });

  const assignClassTeacher = useMutation({
    mutationFn: ({ sectionId, staffId }: { sectionId: string; staffId: string }) =>
      schoolApi.assignClassTeacher(tenantId, sectionId, { staffId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['classes', tenantId] }),
  });

  const unassign = useMutation({
    mutationFn: (id: string) => teacherAssignmentsApi.unassign(tenantId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['teacher-assignments', tenantId] }),
  });

  const loading = classesQ.isLoading || staffQ.isLoading;

  return (
    <div className="space-y-6">
      {/* ---------- Section 1: Class teachers ---------- */}
      <div className="space-y-3">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <GraduationCap size={18} className="text-primary" />
            Class teachers
          </h2>
          <p className="text-sm text-slate-500">Assign one homeroom teacher per section.</p>
        </div>

        {loading && <Spinner />}
        {assignClassTeacher.isError && <ErrorBanner error={assignClassTeacher.error} />}

        {classesQ.data && classesQ.data.map((cls: ClassResponse) => (
          <div key={cls.id} className="bg-white border border-slate-200 rounded-lg overflow-hidden">
            <div className="px-4 py-2 bg-slate-50 border-b border-slate-200 text-sm font-medium text-slate-700">
              Class {cls.name}
            </div>
            <div className="divide-y divide-slate-100">
              {cls.sections.map((sec: SectionResponse) => (
                <div key={sec.id} className="px-4 py-3 flex items-center justify-between gap-4">
                  <div className="text-sm font-medium w-24 shrink-0">Section {sec.name}</div>
                  <div className="flex-1">
                    <RequireRole roles={OWNER_OR_ADMIN}>
                      <select
                        className="w-full max-w-xs rounded border border-slate-300 px-2 py-1.5 text-sm"
                        value={sec.classTeacherId ?? ''}
                        disabled={assignClassTeacher.isPending}
                        onChange={(e) => {
                          if (e.target.value) {
                            assignClassTeacher.mutate({
                              sectionId: sec.id,
                              staffId: e.target.value,
                            });
                          }
                        }}
                      >
                        <option value="">— not assigned —</option>
                        {classTeachers.map((t) => (
                          <option key={t.id} value={t.id}>{t.displayName}</option>
                        ))}
                      </select>
                      {sec.classTeacherId && (() => {
                        const otherSections = (teacherToSections.get(sec.classTeacherId) ?? [])
                          .filter((l) => l !== `${cls.name} – ${sec.name}`);
                        return otherSections.length > 0 ? (
                          <p className="text-xs text-warning mt-1 flex items-center gap-1">
                            ⚠ Also class teacher of {otherSections.join(', ')}
                          </p>
                        ) : null;
                      })()}
                    </RequireRole>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* ---------- Section 2: Subject teacher assignments ---------- */}
      <div className="space-y-3">
        <div className="flex justify-between items-start">
          <div>
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <BookOpen size={18} className="text-primary" />
              Subject teachers
            </h2>
            <p className="text-sm text-slate-500">
              Assign which teacher teaches which subject in each section.
            </p>
          </div>
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button onClick={() => setAssignOpen(true)}>
              <Plus size={16} className="mr-1" /> Assign
            </Button>
          </RequireRole>
        </div>

        {assignmentsQ.isLoading && <Spinner />}
        {assignmentsQ.isError && <ErrorBanner error={assignmentsQ.error} onRetry={() => assignmentsQ.refetch()} />}

        {assignmentsQ.data && assignmentsQ.data.length === 0 && !assignmentsQ.isLoading && (
          <div className="text-center py-8 text-slate-500 bg-white border border-slate-200 rounded-lg">
            <UserCog size={28} className="mx-auto mb-2 text-slate-300" />
            <p className="text-sm">No subject assignments yet.</p>
          </div>
        )}

        {assignmentsQ.data && assignmentsQ.data.length > 0 && (
          <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
                <tr>
                  <th className="text-left px-4 py-2 font-medium">Teacher</th>
                  <th className="text-left px-4 py-2 font-medium">Subject</th>
                  <th className="text-left px-4 py-2 font-medium">Class – Section</th>
                  <th className="px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {assignmentsQ.data.map((a: TeacherAssignmentResponse) => (
                  <tr key={a.id} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2 font-medium">
                      {staffById.get(a.staffId)?.displayName ?? '—'}
                    </td>
                    <td className="px-4 py-2 text-slate-600">
                      {subjectById.get(a.subjectId)?.name ?? '—'}
                    </td>
                    <td className="px-4 py-2 text-slate-600">
                      {sectionLabel.get(a.sectionId) ?? '—'}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <RequireRole roles={OWNER_OR_ADMIN}>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => unassign.mutate(a.id)}
                          disabled={unassign.isPending}
                        >
                          <Trash2 size={14} className="text-danger" />
                        </Button>
                      </RequireRole>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {assignOpen && (
        <AddSubjectAssignmentModal
          tenantId={tenantId}
          teachers={allTeachers}
          subjects={subjectsQ.data ?? []}
          classes={classesQ.data ?? []}
          onClose={() => setAssignOpen(false)}
          onSuccess={() => {
            setAssignOpen(false);
            qc.invalidateQueries({ queryKey: ['teacher-assignments', tenantId] });
          }}
        />
      )}
    </div>
  );
}

function AddSubjectAssignmentModal({
  tenantId, teachers, subjects, classes, onClose, onSuccess,
}: {
  tenantId: string;
  teachers: StaffResponse[];
  subjects: SubjectResponse[];
  classes: ClassResponse[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [staffId, setStaffId] = useState('');
  const [subjectId, setSubjectId] = useState('');
  const [sectionId, setSectionId] = useState('');

  const assign = useMutation({
    mutationFn: () => teacherAssignmentsApi.assign(tenantId, { staffId, subjectId, sectionId }),
    onSuccess,
  });

  const allSections: { id: string; label: string }[] = [];
  classes.forEach((c) =>
    c.sections.forEach((s) =>
      allSections.push({ id: s.id, label: `${c.name} – ${s.name}` }),
    ),
  );

  return (
    <Modal title="Assign subject teacher" onClose={onClose} open={true}>
      <form onSubmit={(e) => { e.preventDefault(); assign.mutate(); }} className="space-y-4 p-1">
        {assign.isError && <ErrorBanner error={assign.error} />}

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Teacher</span>
          <select
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={staffId}
            onChange={(e) => setStaffId(e.target.value)}
            required
          >
            <option value="">Select teacher…</option>
            {teachers.map((t) => (
              <option key={t.id} value={t.id}>{t.displayName}</option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Subject</span>
          <select
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
            required
          >
            <option value="">Select subject…</option>
            {subjects.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Class – Section</span>
          <select
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={sectionId}
            onChange={(e) => setSectionId(e.target.value)}
            required
          >
            <option value="">Select section…</option>
            {allSections.map((s) => (
              <option key={s.id} value={s.id}>{s.label}</option>
            ))}
          </select>
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={assign.isPending}>Cancel</Button>
          <Button type="submit" disabled={assign.isPending || !staffId || !subjectId || !sectionId}>
            {assign.isPending ? 'Saving…' : 'Save assignment'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
