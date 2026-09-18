'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Plus, BookText, Clock, ChevronRight, Trash2, GraduationCap, ExternalLink, Award,
} from 'lucide-react';
import { homeworkApi, type AssignmentDto, type SubmissionDto } from '@/api/endpoints/homework';
import { schoolApi } from '@/api/endpoints/school';
import { academicsApi } from '@/api/endpoints/academics';
import { studentsApi } from '@/api/endpoints/students';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { ANY_TEACHER, OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import Link from 'next/link';
import { useAuth } from '@/auth/AuthProvider';
import { timetableApi } from '@/api/endpoints/timetable';
import { cn } from '@/lib/utils';

/** Convert JS Date.getDay() (0=Sun) to Java DayOfWeek (1=Mon … 7=Sun). */
function jsDayToIso(d: number): number { return d === 0 ? 7 : d; }

export default function HomeworkPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();
  const { state } = useAuth();
  const role = state.status === 'authenticated' ? state.claims.role : '';
  const staffId = state.status === 'authenticated' ? state.claims.sub : '';
  const isTeacher = role === 'CLASS_TEACHER' || role === 'SUBJECT_TEACHER';
  const todayDow = jsDayToIso(new Date().getDay());

  const [sectionFilter, setSectionFilter] = useState<string>('');
  const [createOpen, setCreateOpen] = useState(false);
  const [viewingId, setViewingId] = useState<string | null>(null);

  const classes = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  const subjects = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  // For teachers: fetch today's timetable entries to determine which sections they teach today
  const teacherTimetableQ = useQuery({
    queryKey: ['timetable', 'teacher', tenantId, staffId],
    queryFn: () => timetableApi.getTeacherTimetable(tenantId, staffId),
    enabled: !!tenantId && isTeacher && !!staffId,
  });

  // Sections the teacher teaches TODAY (unique sectionIds from timetable entries for today's day)
  const todaySectionIds = useMemo(() => {
    if (!isTeacher || !teacherTimetableQ.data) return null;
    const ids = new Set(
      teacherTimetableQ.data
        .filter((e) => e.dayOfWeek === todayDow)
        .map((e) => e.sectionId)
    );
    return ids;
  }, [isTeacher, teacherTimetableQ.data, todayDow]);

  const assignmentsQ = useQuery({
    queryKey: ['homework', tenantId, sectionFilter],
    queryFn: () => homeworkApi.listAssignments(tenantId, sectionFilter || undefined),
    enabled: !!tenantId,
    retry: false,
  });

  // ---------- Lookups ----------
  const sectionLabel = useMemo(() => {
    const m = new Map<string, string>();
    for (const c of classes.data ?? []) {
      for (const s of c.sections) m.set(s.id, `${c.name} — ${s.name}`);
    }
    return m;
  }, [classes.data]);

  const subjectLabel = useMemo(() => {
    const m = new Map<string, string>();
    for (const s of subjects.data ?? []) m.set(s.id, s.name);
    return m;
  }, [subjects.data]);

  // ---------- Mutations ----------
  const deleteAssignment = useMutation({
    mutationFn: (id: string) => homeworkApi.deleteAssignment(tenantId, id),
    onSuccess: () => {
      toast.success('Assignment removed');
      qc.invalidateQueries({ queryKey: ['homework', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not remove'),
  });

  if (assignmentsQ.isError && hasCode(assignmentsQ.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader title="Homework" description="Assignments + submissions"
                    icon={<BookText size={18} />} />
        <EmptyState
          icon={<BookText size={28} />}
          title="Homework isn't enabled for your plan"
          description="Assign work to a class, track submissions, and grade online."
        />
      </div>
    );
  }

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="space-y-5">
      <PageHeader
        title="Homework"
        description="Assignments and submissions, grouped by section"
        icon={<BookText size={18} />}
        actions={
          <div className="flex items-center gap-2">
            <RequireRole roles={OWNER_OR_ADMIN}>
              <Link href={`/tenants/${tenantId}/homework/log`}
                className="text-sm font-medium text-primary hover:underline whitespace-nowrap">
                Homework Log
              </Link>
            </RequireRole>
            <RequireRole roles={ANY_TEACHER}>
              {isTeacher && todaySectionIds !== null && todaySectionIds.size === 0 ? (
                <span className="text-xs text-slate-400 italic">No classes scheduled for today</span>
              ) : (
                <Button onClick={() => setCreateOpen(true)}>
                  <Plus size={14} /> Assign homework
                </Button>
              )}
            </RequireRole>
          </div>
        }
      />

      {/* Section filter */}
      <Card padding="md" className="flex flex-wrap items-end gap-3">
        <label className="block max-w-sm flex-1">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Filter by section</span>
          <select
            value={sectionFilter}
            onChange={(e) => setSectionFilter(e.target.value)}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">All sections</option>
            {(classes.data ?? []).map((c) => (
              <optgroup key={c.id} label={c.name}>
                {c.sections.map((s) => (
                  <option key={s.id} value={s.id}>{c.name} — {s.name}</option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>
        <span className="text-xs text-slate-500 ml-auto">
          {(assignmentsQ.data ?? []).length} assignment{(assignmentsQ.data ?? []).length === 1 ? '' : 's'}
        </span>
      </Card>

      {/* List */}
      {assignmentsQ.isLoading && <Skeleton className="h-48" />}
      {assignmentsQ.isError && !hasCode(assignmentsQ.error, 'FEATURE_DISABLED') && (
        <ErrorBanner error={assignmentsQ.error} onRetry={() => assignmentsQ.refetch()} />
      )}

      {assignmentsQ.data && assignmentsQ.data.length === 0 && (
        <EmptyState
          icon={<BookText size={28} />}
          title="No assignments yet"
          description={sectionFilter ? 'No homework for this section.' : 'Create your first assignment to get started.'}
          action={
            <RequireRole roles={ANY_TEACHER}>
              <Button onClick={() => setCreateOpen(true)}><Plus size={14} /> Assign homework</Button>
            </RequireRole>
          }
        />
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {(assignmentsQ.data ?? []).map((a) => {
          const overdue = a.dueDate && a.dueDate < today;
          const dueSoon = a.dueDate && !overdue && (new Date(a.dueDate + 'T00:00:00').getTime() - Date.now() < 1000 * 60 * 60 * 24 * 3);
          return (
            <Card key={a.id} className="p-4 hover:border-primary hover:shadow-sm transition group" padding="none">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5 flex-wrap mb-1">
                    <Badge tone="primary" size="sm">
                      {sectionLabel.get(a.sectionId) ?? 'Section'}
                    </Badge>
                    {a.subjectId && subjectLabel.get(a.subjectId) && (
                      <Badge tone="accent" size="sm">{subjectLabel.get(a.subjectId)}</Badge>
                    )}
                    {a.dueDate && (
                      <Badge tone={overdue ? 'danger' : dueSoon ? 'warning' : 'neutral'} size="sm">
                        <Clock size={10} /> Due {new Date(a.dueDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                      </Badge>
                    )}
                  </div>
                  <h3 className="text-base font-semibold text-slate-900 truncate">{a.title}</h3>
                  <p className="text-sm text-slate-600 line-clamp-2 mt-1 leading-snug">{a.body}</p>
                </div>
                <RequireRole roles={ANY_TEACHER}>
                  <button
                    onClick={() => {
                      if (confirm(`Remove "${a.title}"?`)) deleteAssignment.mutate(a.id);
                    }}
                    className="text-slate-400 hover:text-danger p-1 rounded transition opacity-0 group-hover:opacity-100"
                    aria-label="Delete"
                  >
                    <Trash2 size={14} />
                  </button>
                </RequireRole>
              </div>
              <div className="flex items-center justify-between mt-3 pt-3 border-t border-slate-100">
                <div className="text-[11px] text-slate-500">
                  Posted {new Date(a.createdAt).toLocaleDateString('en-IN')}
                </div>
                <RequireRole roles={ANY_TEACHER}>
                  <button
                    onClick={() => setViewingId(a.id)}
                    className="text-xs text-primary font-medium inline-flex items-center gap-0.5 hover:underline"
                  >
                    View submissions <ChevronRight size={12} />
                  </button>
                </RequireRole>
              </div>
            </Card>
          );
        })}
      </div>

      <CreateAssignmentModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        tenantId={tenantId}
        classes={classes.data ?? []}
        subjects={subjects.data ?? []}
        todaySectionIds={todaySectionIds}
      />

      {viewingId && (
        <SubmissionsModal
          tenantId={tenantId}
          assignmentId={viewingId}
          assignment={(assignmentsQ.data ?? []).find((a) => a.id === viewingId)}
          onClose={() => setViewingId(null)}
        />
      )}
    </div>
  );
}

// ============================================================
// Create assignment modal
// ============================================================

function CreateAssignmentModal({
  open, onClose, tenantId, classes, subjects, todaySectionIds,
}: {
  open: boolean; onClose: () => void; tenantId: string;
  classes: Array<{ id: string; name: string; sections: Array<{ id: string; name: string }> }>;
  subjects: Array<{ id: string; name: string }>;
  todaySectionIds: Set<string> | null;  // null = no restriction (admin/principal)
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const initial = { sectionId: '', subjectId: '', title: '', body: '', dueDate: '' };
  const [form, setForm] = useState(initial);

  const create = useMutation({
    mutationFn: () => homeworkApi.createAssignment(tenantId, {
      sectionId: form.sectionId,
      subjectId: form.subjectId || undefined,
      title: form.title,
      body: form.body,
      dueDate: form.dueDate || undefined,
      // attachment intentionally omitted — text-only homework
    }),
    onSuccess: () => {
      toast.success('Assignment posted');
      qc.invalidateQueries({ queryKey: ['homework', tenantId] });
      setForm(initial);
      onClose();
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not post'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Assign homework">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Section</span>
          <select required value={form.sectionId}
            onChange={(e) => setForm({ ...form, sectionId: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">Select a section</option>
            {classes.map((c) => {
              // Filter sections to only those the teacher teaches today (if restriction set)
              const allowedSections = todaySectionIds
                ? c.sections.filter((s) => todaySectionIds.has(s.id))
                : c.sections;
              if (allowedSections.length === 0) return null;
              return (
                <optgroup key={c.id} label={c.name}>
                  {allowedSections.map((s) => (
                    <option key={s.id} value={s.id}>{c.name} — {s.name}</option>
                  ))}
                </optgroup>
              );
            })}
          </select>
        </label>

        {subjects.length > 0 && (
          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Subject (optional)</span>
            <select value={form.subjectId}
              onChange={(e) => setForm({ ...form, subjectId: e.target.value })}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            >
              <option value="">— No subject —</option>
              {subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </label>
        )}

        <Input label="Title" required value={form.title}
               onChange={(e) => setForm({ ...form, title: e.target.value })} />

        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Instructions</span>
          <textarea required rows={4} value={form.body}
            onChange={(e) => setForm({ ...form, body: e.target.value })}
            placeholder="Describe what students should do…"
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <Input type="date" label="Due date" value={form.dueDate}
                 onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
                 min={new Date().toISOString().slice(0, 10)} />
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending}
                  disabled={!form.sectionId || !form.title || !form.body}>
            Post assignment
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ============================================================
// Submissions modal
// ============================================================

function SubmissionsModal({
  tenantId, assignmentId, assignment, onClose,
}: {
  tenantId: string;
  assignmentId: string;
  assignment: AssignmentDto | undefined;
  onClose: () => void;
}) {
  const subsQ = useQuery({
    queryKey: ['homework-subs', tenantId, assignmentId],
    queryFn: () => homeworkApi.listSubmissions(tenantId, assignmentId),
    enabled: !!tenantId && !!assignmentId,
  });
  const studentsQ = useQuery({
    queryKey: ['students', tenantId, 'all'],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    enabled: !!tenantId,
  });

  const studentName = useMemo(() => {
    const m = new Map<string, string>();
    for (const s of studentsQ.data?.items ?? []) m.set(s.id, s.displayName);
    return m;
  }, [studentsQ.data]);

  return (
    <Modal open={true} onClose={onClose} title={assignment?.title ?? 'Submissions'}>
      <div className="space-y-3">
        {assignment && (
          <p className="text-sm text-slate-600 italic border-l-2 border-primary/40 pl-3">
            {assignment.body.slice(0, 200)}{assignment.body.length > 200 ? '…' : ''}
          </p>
        )}
        {subsQ.isLoading && <Skeleton className="h-40" />}
        {subsQ.data && subsQ.data.length === 0 && (
          <p className="text-sm text-slate-500 py-4 text-center">No submissions yet.</p>
        )}
        {subsQ.data && subsQ.data.length > 0 && (
          <ul className="divide-y divide-slate-100 max-h-[60vh] overflow-y-auto -mx-5 px-5">
            {subsQ.data.map((s) => (
              <SubmissionRow key={s.id}
                submission={s}
                tenantId={tenantId}
                assignmentId={assignmentId}
                studentName={studentName.get(s.studentId) ?? s.studentId.slice(0, 8)} />
            ))}
          </ul>
        )}
      </div>
    </Modal>
  );
}

function SubmissionRow({
  submission, tenantId, assignmentId, studentName,
}: {
  submission: SubmissionDto;
  tenantId: string;
  assignmentId: string;
  studentName: string;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [grade, setGrade]   = useState(submission.grade ?? '');
  const [remark, setRemark] = useState(submission.teacherRemark ?? '');
  const [editing, setEditing] = useState(false);

  const gradeMut = useMutation({
    mutationFn: () => homeworkApi.grade(tenantId, submission.id, grade, remark || undefined),
    onSuccess: () => {
      toast.success('Graded');
      qc.invalidateQueries({ queryKey: ['homework-subs', tenantId, assignmentId] });
      setEditing(false);
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not save grade'),
  });

  return (
    <li className="py-3">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-slate-900">{studentName}</span>
            {submission.grade && <Badge tone="accent" size="sm"><Award size={10} /> {submission.grade}</Badge>}
            {!submission.grade && <Badge tone="warning" size="sm">Ungraded</Badge>}
          </div>
          <div className="text-[11px] text-slate-500 mt-0.5">
            Submitted {new Date(submission.submittedAt).toLocaleString('en-IN')}
            {submission.gradedAt && ` · Graded ${new Date(submission.gradedAt).toLocaleDateString('en-IN')}`}
          </div>
          {submission.submissionText && (
            <p className="text-sm text-slate-700 mt-2 italic">"{submission.submissionText}"</p>
          )}
          {submission.attachmentUrl && (
            <a href={submission.attachmentUrl} target="_blank" rel="noopener noreferrer"
               className="inline-flex items-center gap-1 text-xs text-primary font-medium mt-1.5 hover:underline">
              Attachment <ExternalLink size={10} />
            </a>
          )}
        </div>
        {!editing && (
          <RequireRole roles={ANY_TEACHER}>
            <Button size="xs" variant="subtle" onClick={() => setEditing(true)}>
              <GraduationCap size={12} /> {submission.grade ? 'Update' : 'Grade'}
            </Button>
          </RequireRole>
        )}
      </div>
      {editing && (
        <div className="mt-2 grid grid-cols-[120px_1fr_auto] gap-2 items-end">
          <Input label="Grade" value={grade} onChange={(e) => setGrade(e.target.value)}
                 placeholder="A / 85 / Good" />
          <Input label="Remark" value={remark} onChange={(e) => setRemark(e.target.value)} />
          <div className="flex gap-1.5">
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>Cancel</Button>
            <Button size="sm" onClick={() => gradeMut.mutate()} loading={gradeMut.isPending}
                    disabled={!grade.trim()}>Save</Button>
          </div>
        </div>
      )}
    </li>
  );
}
