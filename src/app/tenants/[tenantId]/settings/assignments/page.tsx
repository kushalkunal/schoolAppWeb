'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, Trash2, UserCog, BookOpen } from 'lucide-react';
import { teacherAssignmentsApi } from '@/api/endpoints/teacherAssignments';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
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

export default function AssignmentsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const assignmentsQ = useQuery({
    queryKey: ['teacher-assignments', tenantId],
    queryFn: () => teacherAssignmentsApi.list(tenantId),
    enabled: !!tenantId,
  });
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

  const unassign = useMutation({
    mutationFn: (id: string) => teacherAssignmentsApi.unassign(tenantId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['teacher-assignments', tenantId] }),
  });

  // Build lookup maps for display
  const staffById = new Map<string, StaffResponse>(
    staffQ.data?.map((s) => [s.id, s]) ?? [],
  );
  const subjectById = new Map<string, SubjectResponse>(
    subjectsQ.data?.map((s) => [s.id, s]) ?? [],
  );
  // sectionId → class + section label
  const sectionLabel = new Map<string, string>();
  classesQ.data?.forEach((c: ClassResponse) => {
    c.sections.forEach((s: SectionResponse) => {
      sectionLabel.set(s.id, `${c.name} – ${s.name}`);
    });
  });

  const teachers = staffQ.data?.filter(
    (s) => s.active && (s.role === 'SUBJECT_TEACHER' || s.role === 'CLASS_TEACHER'),
  ) ?? [];

  const loading = assignmentsQ.isLoading || classesQ.isLoading || staffQ.isLoading || subjectsQ.isLoading;

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Subject–Teacher Assignments</h1>
          <p className="text-sm text-slate-500">
            Map which teacher teaches which subject in each section for the current academic year.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setOpen(true)}>
            <Plus size={16} className="mr-1" /> Assign teacher
          </Button>
        </RequireRole>
      </div>

      {loading && <Spinner />}
      {assignmentsQ.isError && (
        <ErrorBanner error={assignmentsQ.error} onRetry={() => assignmentsQ.refetch()} />
      )}
      {unassign.isError && <ErrorBanner error={unassign.error} />}

      {!loading && assignmentsQ.data && assignmentsQ.data.length === 0 && (
        <Card>
          <CardBody>
            <div className="text-center py-10 text-slate-500">
              <UserCog className="mx-auto mb-3" size={36} />
              <p className="font-medium">No assignments yet</p>
              <p className="text-sm mt-1">
                Assign subject teachers to sections so teachers see their schedule on login.
              </p>
            </div>
          </CardBody>
        </Card>
      )}

      {assignmentsQ.data && assignmentsQ.data.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Teacher</th>
                <th className="text-left px-4 py-2 font-medium">Subject</th>
                <th className="text-left px-4 py-2 font-medium">Class – Section</th>
                <RequireRole roles={OWNER_OR_ADMIN}>
                  <th className="px-4 py-2" />
                </RequireRole>
              </tr>
            </thead>
            <tbody>
              {assignmentsQ.data.map((a: TeacherAssignmentResponse) => (
                <tr key={a.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium">
                    <div className="flex items-center gap-2">
                      <UserCog size={14} className="text-slate-400" />
                      {staffById.get(a.staffId)?.displayName ?? a.staffId.slice(0, 8)}
                    </div>
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <BookOpen size={14} className="text-slate-400" />
                      {subjectById.get(a.subjectId)?.name ?? a.subjectId.slice(0, 8)}
                    </div>
                  </td>
                  <td className="px-4 py-2 text-slate-600">
                    {sectionLabel.get(a.sectionId) ?? a.sectionId.slice(0, 8)}
                  </td>
                  <RequireRole roles={OWNER_OR_ADMIN}>
                    <td className="px-4 py-2 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => unassign.mutate(a.id)}
                        disabled={unassign.isPending}
                        title="Remove assignment"
                      >
                        <Trash2 size={14} className="text-danger" />
                      </Button>
                    </td>
                  </RequireRole>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {open && (
        <AddAssignmentModal
          tenantId={tenantId}
          teachers={teachers}
          subjects={subjectsQ.data ?? []}
          classes={classesQ.data ?? []}
          onClose={() => setOpen(false)}
          onSuccess={() => {
            setOpen(false);
            qc.invalidateQueries({ queryKey: ['teacher-assignments', tenantId] });
          }}
        />
      )}
    </div>
  );
}

function AddAssignmentModal({
  tenantId,
  teachers,
  subjects,
  classes,
  onClose,
  onSuccess,
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
    mutationFn: () =>
      teacherAssignmentsApi.assign(tenantId, { staffId, subjectId, sectionId }),
    onSuccess,
  });

  const allSections: { id: string; label: string }[] = [];
  classes.forEach((c) =>
    c.sections.forEach((s) =>
      allSections.push({ id: s.id, label: `${c.name} – ${s.name}` }),
    ),
  );

  const valid = !!staffId && !!subjectId && !!sectionId;

  return (
    <Modal title="Assign subject teacher" onClose={onClose} open={true}>
      <div className="space-y-4 p-4">
        {assign.isError && <ErrorBanner error={assign.error} />}

        <div>
          <label className="block text-sm font-medium mb-1">Teacher</label>
          <select
            value={staffId}
            onChange={(e) => setStaffId(e.target.value)}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select teacher…</option>
            {teachers.map((t) => (
              <option key={t.id} value={t.id}>
                {t.displayName} ({t.role.replace('_', ' ')})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Subject</label>
          <select
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select subject…</option>
            {subjects.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} {s.code ? `(${s.code})` : ''}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Class – Section</label>
          <select
            value={sectionId}
            onChange={(e) => setSectionId(e.target.value)}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select section…</option>
            {allSections.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => assign.mutate()} disabled={!valid || assign.isPending}>
            {assign.isPending ? 'Saving…' : 'Assign'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
