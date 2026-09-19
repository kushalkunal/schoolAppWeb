'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ChevronLeft, Phone, Mail, Calendar, User, GraduationCap, FileText,
  CheckCircle2, XCircle, ExternalLink, Sparkles, MapPin, Clock,
} from 'lucide-react';
// (Clock is used by the timeline; the rest by header / forms / cards.)
import { admissionsApi } from '@/api/endpoints/admissions';
import { schoolApi } from '@/api/endpoints/school';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';
import type { AdmissionResponse, AdmissionStatus } from '@/types/domain';

/**
 * Admission detail. Three columns at lg+:
 *  1. Status timeline (vertical) showing where this admission is in the funnel
 *  2. Student / parent / placement details
 *  3. Contextual action panel based on current status (schedule test, record result, …)
 *
 * Every state-change action lives behind {@link RequireRole}({OWNER_OR_ADMIN}) and uses
 * the existing backend state-machine endpoints (validated server-side).
 */
export default function AdmissionDetailPage() {
  const params = useParams();
  const router = useRouter();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const admissionId = typeof params.admissionId === 'string' ? params.admissionId : '';

  const qc = useQueryClient();
  const toast = useToast();

  const q = useQuery({
    queryKey: ['admission', tenantId, admissionId],
    queryFn: () => admissionsApi.get(tenantId, admissionId),
    enabled: !!tenantId && !!admissionId,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['admission', tenantId, admissionId] });
    qc.invalidateQueries({ queryKey: ['admissions', tenantId] });
    qc.invalidateQueries({ queryKey: ['dashboard', tenantId] });
  };

  // ---------------- Loading + error ----------------
  if (q.isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-12 w-2/3" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <Skeleton className="h-80" />
          <Skeleton className="h-80 lg:col-span-2" />
        </div>
      </div>
    );
  }

  if (q.isError || !q.data) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={() => router.back()} size="sm">
          <ChevronLeft size={14} /> Back
        </Button>
        <ErrorBanner error={q.error ?? 'Admission not found'} onRetry={() => q.refetch()} />
      </div>
    );
  }

  const a = q.data;
  const isTerminal = ['ENROLLED', 'DECLINED', 'WITHDRAWN', 'REJECTED'].includes(a.status);

  return (
    <div className="space-y-6">
      <PageHeader
        breadcrumb={
          <Link href={`/tenants/${tenantId}/admissions`}
                className="inline-flex items-center gap-1 hover:text-primary transition">
            <ChevronLeft size={12} /> Admissions
          </Link>
        }
        title={a.studentDisplayName}
        description={
          <span>
            Applying to <span className="font-medium text-slate-700">{a.intendedClass}</span>
            {a.intendedSection ? ` · Section ${a.intendedSection}` : ''}
            {a.intendedAcademicYear ? ` · ${a.intendedAcademicYear}` : ''}
            {a.source ? ` · Source: ${a.source}` : ''}
          </span>
        }
        icon={<User size={18} />}
        actions={<StatusBadge status={a.status} size="md" />}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* ---------------- LEFT: timeline ---------------- */}
        <Card className="lg:col-span-1 p-0 overflow-hidden">
          <CardHeader>
            <CardTitle>Status timeline</CardTitle>
            <CardDescription>Where this applicant is in the funnel</CardDescription>
          </CardHeader>
          <CardBody>
            <Timeline admission={a} />
          </CardBody>
        </Card>

        {/* ---------------- RIGHT: details + actions ---------------- */}
        <div className="lg:col-span-2 space-y-4">
          {/* Parent + student details */}
          <Card padding="md">
            <CardTitle>Applicant details</CardTitle>
            <CardDescription className="mb-4">Pulled from the original enquiry</CardDescription>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
              <Field label="Student name"        value={a.studentDisplayName} />
              <Field label="Date of birth"
                     value={a.studentDateOfBirth ? new Date(a.studentDateOfBirth).toLocaleDateString('en-IN') : '—'} />
              <Field label="Gender"              value={a.studentGender ?? '—'} />
              <Field label="Intended class"      value={a.intendedClass + (a.intendedSection ? ' — ' + a.intendedSection : '')} />

              <div className="sm:col-span-2 h-px bg-slate-100 my-1" />

              <Field label="Parent / guardian"   value={a.parentName ?? '—'} />
              <Field label="Phone"               value={<><Phone size={11} className="inline mr-1 text-slate-400" />{a.parentPhone}</>} />
              <Field label="Email"               value={a.parentEmail
                                                     ? <><Mail size={11} className="inline mr-1 text-slate-400" />{a.parentEmail}</>
                                                     : '—'} />
              <Field label="Source"              value={a.source ?? '—'} />
              {a.referrerName && <Field label="Referrer" value={a.referrerName} />}
            </div>
            {a.notes && (
              <div className="mt-4 p-3 rounded-brand bg-slate-50 border border-slate-200">
                <div className="text-[10px] text-slate-500 uppercase tracking-wide mb-0.5">Notes</div>
                <div className="text-sm text-slate-700 whitespace-pre-wrap">{a.notes}</div>
              </div>
            )}
          </Card>

          {/* Test details if relevant */}
          {(a.testScheduledAt || a.testTotalMarks != null || a.testScores.length > 0) && (
            <Card padding="md">
              <CardTitle>Entrance test</CardTitle>
              <CardDescription className="mb-4">Scheduling, scoring, and remarks</CardDescription>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
                {a.testScheduledAt && (
                  <Field label="Scheduled at"
                         value={<><Calendar size={11} className="inline mr-1 text-slate-400" />{new Date(a.testScheduledAt).toLocaleString('en-IN')}</>} />
                )}
                {a.testVenue && (
                  <Field label="Venue"
                         value={<><MapPin size={11} className="inline mr-1 text-slate-400" />{a.testVenue}</>} />
                )}
                {a.testTotalMarks != null && (
                  <Field label="Score"
                         value={`${a.testObtainedMarks ?? '—'} / ${a.testTotalMarks}`} />
                )}
                {a.testRemarks && <Field label="Remarks" value={a.testRemarks} />}
              </div>

              {a.testScores.length > 0 && (
                <table className="w-full text-sm mt-4">
                  <thead>
                    <tr className="text-[10px] uppercase tracking-wide text-slate-500 border-b border-slate-200">
                      <th className="text-left py-1.5">Subject</th>
                      <th className="text-right">Max</th>
                      <th className="text-right">Obtained</th>
                      <th className="text-left pl-2">Remarks</th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.testScores.map((s, i) => (
                      <tr key={i} className="border-b border-slate-100">
                        <td className="py-1.5">{s.subjectName}</td>
                        <td className="text-right text-slate-500 tabular-nums">{s.maxMarks}</td>
                        <td className="text-right font-medium tabular-nums">{s.obtainedMarks}</td>
                        <td className="pl-2 text-slate-500">{s.remarks ?? ''}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
          )}

          {/* Offer details if relevant */}
          {(a.offerIssuedAt || a.offerLetterUrl) && (
            <Card padding="md">
              <CardTitle>Offer</CardTitle>
              <CardDescription className="mb-4">Offer letter + parent response</CardDescription>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
                {a.offerIssuedAt   && <Field label="Issued"   value={new Date(a.offerIssuedAt).toLocaleString('en-IN')} />}
                {a.offerAcceptedAt && <Field label="Accepted" value={<><CheckCircle2 size={11} className="inline mr-1 text-success" />{new Date(a.offerAcceptedAt).toLocaleString('en-IN')}</>} />}
                {a.offerDeclinedAt && <Field label="Declined" value={<><XCircle      size={11} className="inline mr-1 text-danger" />{new Date(a.offerDeclinedAt).toLocaleString('en-IN')}</>} />}
                {a.declineReason   && <Field label="Reason"   value={a.declineReason} />}
              </div>
              {a.offerLetterUrl && (
                <a
                  href={a.offerLetterUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 mt-3 text-sm text-primary font-medium hover:underline"
                >
                  <FileText size={14} /> View offer letter <ExternalLink size={11} />
                </a>
              )}
            </Card>
          )}

          {/* Enrolment outcome */}
          {a.status === 'ENROLLED' && a.enrolledStudentId && (
            <Card padding="md" className="bg-success/5 border-success/30">
              <div className="flex items-center gap-3">
                <span className="w-10 h-10 grid place-items-center rounded-brand bg-success text-white">
                  <CheckCircle2 size={20} />
                </span>
                <div className="flex-1">
                  <CardTitle className="text-success">Enrolled</CardTitle>
                  <CardDescription>
                    Student joined on {a.enrolledAt ? new Date(a.enrolledAt).toLocaleDateString('en-IN') : '—'}
                  </CardDescription>
                </div>
                <Link
                  href={`/tenants/${tenantId}/students/${a.enrolledStudentId}`}
                  className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
                >
                  Open profile <ExternalLink size={11} />
                </Link>
              </div>
            </Card>
          )}

          {/* Action panel */}
          {!isTerminal && (
            <RequireRole roles={OWNER_OR_ADMIN}>
              <ActionPanel admission={a} tenantId={tenantId} onMutated={invalidate} />
            </RequireRole>
          )}
        </div>
      </div>
    </div>
  );
}

// ============================================================
// Timeline
// ============================================================

interface Step { key: AdmissionStatus[]; label: string; doneAt?: string | null }

function Timeline({ admission }: { admission: AdmissionResponse }) {
  const steps: Step[] = [
    { key: ['ENQUIRY', 'APPLICATION_SUBMITTED', 'TEST_SCHEDULED', 'TEST_COMPLETED', 'OFFERED', 'ACCEPTED', 'ENROLLED'],
      label: 'Enquiry received', doneAt: admission.createdAt },
    { key: ['APPLICATION_SUBMITTED', 'TEST_SCHEDULED', 'TEST_COMPLETED', 'OFFERED', 'ACCEPTED', 'ENROLLED'],
      label: 'Application submitted' },
    { key: ['TEST_SCHEDULED', 'TEST_COMPLETED', 'OFFERED', 'ACCEPTED', 'ENROLLED'],
      label: 'Entrance test', doneAt: admission.testScheduledAt },
    { key: ['TEST_COMPLETED', 'OFFERED', 'ACCEPTED', 'ENROLLED'],
      label: 'Test completed' },
    { key: ['OFFERED', 'ACCEPTED', 'ENROLLED'],
      label: 'Offer issued', doneAt: admission.offerIssuedAt },
    { key: ['ACCEPTED', 'ENROLLED'],
      label: 'Offer accepted', doneAt: admission.offerAcceptedAt },
    { key: ['ENROLLED'],
      label: 'Enrolled', doneAt: admission.enrolledAt },
  ];

  const isTerminal = ['DECLINED', 'WITHDRAWN', 'REJECTED'].includes(admission.status);
  const currentIdx = steps.findIndex((s) => s.key[0] === admission.status);

  return (
    <ol className="relative">
      {steps.map((s, idx) => {
        const done = s.key.includes(admission.status) && !isTerminal;
        const isCurrent = idx === currentIdx;
        return (
          <li key={s.label} className="relative pl-7 pb-4 last:pb-0">
            {/* Connector line */}
            {idx < steps.length - 1 && (
              <span
                className={cn(
                  'absolute left-[10px] top-5 bottom-0 w-px',
                  done ? 'bg-primary' : 'bg-slate-200',
                )}
              />
            )}
            {/* Bullet */}
            <span
              className={cn(
                'absolute left-0 top-1 w-5 h-5 rounded-full grid place-items-center shrink-0 ring-4',
                done
                  ? 'bg-primary text-primary-fg ring-primary-soft'
                  : isCurrent
                    ? 'bg-white border-2 border-primary text-primary ring-primary-soft'
                    : 'bg-white border-2 border-slate-300 ring-white',
              )}
            >
              {done && <CheckCircle2 size={11} />}
            </span>
            <div className={cn('font-medium text-sm', done ? 'text-slate-900' : 'text-slate-500')}>
              {s.label}
            </div>
            {s.doneAt && done && (
              <div className="text-[11px] text-slate-400 mt-0.5 inline-flex items-center gap-1">
                <Clock size={9} /> {new Date(s.doneAt).toLocaleString('en-IN')}
              </div>
            )}
          </li>
        );
      })}

      {isTerminal && (
        <li className="relative pl-7 mt-2 pt-3 border-t border-slate-100">
          <span className="absolute left-0 top-3.5 w-5 h-5 rounded-full grid place-items-center bg-danger text-white">
            <XCircle size={11} />
          </span>
          <div className="font-medium text-sm text-danger">
            {admission.status === 'REJECTED'  && 'Rejected by school'}
            {admission.status === 'DECLINED'  && 'Declined by parent'}
            {admission.status === 'WITHDRAWN' && 'Withdrawn'}
          </div>
          {admission.declineReason && (
            <div className="text-[11px] text-slate-500 mt-0.5">{admission.declineReason}</div>
          )}
        </li>
      )}
    </ol>
  );
}

// ============================================================
// Action panel
// ============================================================

function ActionPanel({ admission, tenantId, onMutated }: {
  admission: AdmissionResponse; tenantId: string; onMutated: () => void;
}) {
  switch (admission.status) {
    case 'ENQUIRY':
      return <SimpleAction
        title="Convert to application"
        description="Move this lead from enquiry to a full application — they're ready to engage further."
        actionLabel="Move to application"
        mutationFn={() => admissionsApi.submitApplication(tenantId, admission.id)}
        onSuccess={onMutated}
      />;

    case 'APPLICATION_SUBMITTED':
      return <ScheduleTestAction tenantId={tenantId} admission={admission} onSuccess={onMutated} />;

    case 'TEST_SCHEDULED':
      return <RecordTestResultAction tenantId={tenantId} admission={admission} onSuccess={onMutated} />;

    case 'TEST_COMPLETED':
      return <MakeOfferAction tenantId={tenantId} admission={admission} onSuccess={onMutated} />;

    case 'OFFERED':
      return <OfferResponseActions tenantId={tenantId} admission={admission} onSuccess={onMutated} />;

    case 'ACCEPTED':
      return <EnrollAction tenantId={tenantId} admission={admission} onSuccess={onMutated} />;

    default:
      return null;
  }
}

function SimpleAction({
  title, description, actionLabel, mutationFn, onSuccess,
}: {
  title: string; description: string; actionLabel: string;
  mutationFn: () => Promise<unknown>; onSuccess: () => void;
}) {
  const toast = useToast();
  const m = useMutation({
    mutationFn,
    onSuccess: () => { toast.success(`${actionLabel} ✓`); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Action failed'),
  });
  return (
    <Card padding="md" className="border-primary/30 bg-primary-soft/30">
      <div className="flex items-start gap-3">
        <span className="w-9 h-9 grid place-items-center rounded-brand bg-primary text-primary-fg shrink-0">
          <Sparkles size={16} />
        </span>
        <div className="flex-1">
          <CardTitle>{title}</CardTitle>
          <CardDescription className="mb-3">{description}</CardDescription>
          <Button onClick={() => m.mutate()} loading={m.isPending} size="sm">
            {actionLabel}
          </Button>
        </div>
      </div>
    </Card>
  );
}

function ScheduleTestAction({ tenantId, admission, onSuccess }: {
  tenantId: string; admission: AdmissionResponse; onSuccess: () => void;
}) {
  const toast = useToast();
  const [scheduledAt, setScheduledAt] = useState<string>('');
  const [venue, setVenue] = useState<string>('');
  const m = useMutation({
    mutationFn: () => admissionsApi.scheduleTest(tenantId, admission.id, {
      scheduledAt: new Date(scheduledAt).toISOString(),
      venue: venue || undefined,
    }),
    onSuccess: () => { toast.success('Test scheduled'); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not schedule'),
  });

  return (
    <Card padding="md" className="border-warning/30 bg-warning/5">
      <CardTitle>Schedule entrance test</CardTitle>
      <CardDescription className="mb-4">
        Set a date and venue. Parent gets notified automatically (if WhatsApp / email is configured).
      </CardDescription>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input label="Date & time" type="datetime-local" required
               value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} />
        <Input label="Venue" placeholder="School auditorium"
               value={venue} onChange={(e) => setVenue(e.target.value)} />
      </div>
      <div className="mt-3 flex justify-end">
        <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!scheduledAt}>
          Schedule test
        </Button>
      </div>
    </Card>
  );
}

function RecordTestResultAction({ tenantId, admission, onSuccess }: {
  tenantId: string; admission: AdmissionResponse; onSuccess: () => void;
}) {
  const toast = useToast();
  const [total, setTotal]   = useState<string>('100');
  const [obtained, setObt]  = useState<string>('');
  const [remarks, setRem]   = useState<string>('');
  const m = useMutation({
    mutationFn: () => admissionsApi.recordTestResult(tenantId, admission.id, {
      totalMarks: Number(total),
      obtainedMarks: Number(obtained),
      remarks: remarks || undefined,
    }),
    onSuccess: () => { toast.success('Test result recorded'); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not save'),
  });
  return (
    <Card padding="md" className="border-warning/30 bg-warning/5">
      <CardTitle>Record test result</CardTitle>
      <CardDescription className="mb-4">
        Enter the aggregate score; per-subject breakdown can be added later.
      </CardDescription>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Input label="Total marks"    type="number" min={1} value={total}
               onChange={(e) => setTotal(e.target.value)} />
        <Input label="Obtained marks" type="number" min={0} max={Number(total) || undefined}
               value={obtained} onChange={(e) => setObt(e.target.value)} />
        <Input label="Remarks (optional)" value={remarks}
               onChange={(e) => setRem(e.target.value)} />
      </div>
      <div className="mt-3 flex justify-end">
        <Button onClick={() => m.mutate()} loading={m.isPending}
                disabled={!total || obtained === ''}>
          Record result
        </Button>
      </div>
    </Card>
  );
}

function MakeOfferAction({ tenantId, admission, onSuccess }: {
  tenantId: string; admission: AdmissionResponse; onSuccess: () => void;
}) {
  const toast = useToast();
  const [letterUrl, setLetterUrl] = useState<string>('');
  const m = useMutation({
    mutationFn: () => admissionsApi.makeOffer(tenantId, admission.id, letterUrl || undefined),
    onSuccess: () => { toast.success('Offer issued'); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not issue offer'),
  });
  return (
    <Card padding="md" className="border-primary/30 bg-primary-soft/30">
      <CardTitle>Make admission offer</CardTitle>
      <CardDescription className="mb-4">
        Issue the offer to the parent. Paste the offer-letter URL (or upload via documents service first).
      </CardDescription>
      <Input label="Offer letter URL (optional)" placeholder="https://…/offer-letter.pdf"
             value={letterUrl} onChange={(e) => setLetterUrl(e.target.value)} />
      <div className="mt-3 flex justify-end">
        <Button onClick={() => m.mutate()} loading={m.isPending}>
          Make offer
        </Button>
      </div>
    </Card>
  );
}

function OfferResponseActions({ tenantId, admission, onSuccess }: {
  tenantId: string; admission: AdmissionResponse; onSuccess: () => void;
}) {
  const toast = useToast();
  const [declineOpen, setDeclineOpen] = useState(false);
  const [reason, setReason] = useState('');

  const accept = useMutation({
    mutationFn: () => admissionsApi.accept(tenantId, admission.id),
    onSuccess: () => { toast.success('Offer accepted'); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not accept'),
  });
  const decline = useMutation({
    mutationFn: () => admissionsApi.decline(tenantId, admission.id, reason || undefined),
    onSuccess: () => { toast.info('Marked as declined'); setDeclineOpen(false); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not decline'),
  });

  return (
    <>
      <Card padding="md" className="border-primary/30 bg-primary-soft/30">
        <CardTitle>Record parent response</CardTitle>
        <CardDescription className="mb-4">
          Mark the parent's response to the offer. Accepting unlocks enrolment.
        </CardDescription>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => accept.mutate()} loading={accept.isPending} variant="accent">
            <CheckCircle2 size={14} /> Accepted
          </Button>
          <Button onClick={() => setDeclineOpen(true)} variant="secondary">
            <XCircle size={14} /> Declined
          </Button>
        </div>
      </Card>

      <Modal open={declineOpen} onClose={() => setDeclineOpen(false)} title="Decline admission">
        <div className="space-y-3">
          <p className="text-sm text-slate-600">
            Record the parent's reason for declining so future analytics show why offers are lost.
          </p>
          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Reason</span>
            <textarea
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
              placeholder="e.g. Chose a different school nearer to home."
            />
          </label>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeclineOpen(false)}>Cancel</Button>
            <Button variant="danger" onClick={() => decline.mutate()} loading={decline.isPending}>
              Confirm decline
            </Button>
          </div>
        </div>
      </Modal>
    </>
  );
}

function EnrollAction({ tenantId, admission, onSuccess }: {
  tenantId: string; admission: AdmissionResponse; onSuccess: () => void;
}) {
  const toast = useToast();
  const [sectionId, setSectionId] = useState<string>('');

  const classes = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
  });

  const enroll = useMutation({
    mutationFn: () => admissionsApi.enroll(tenantId, admission.id, sectionId),
    onSuccess: () => { toast.success('Enrolled — student record created'); onSuccess(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not enrol'),
  });

  return (
    <Card padding="md" className="border-accent/30 bg-accent-soft/40">
      <div className="flex items-start gap-3">
        <span className="w-9 h-9 grid place-items-center rounded-brand bg-accent text-accent-fg shrink-0">
          <GraduationCap size={16} />
        </span>
        <div className="flex-1">
          <CardTitle>Enrol student</CardTitle>
          <CardDescription className="mb-4">
            Pick the actual section for the student. A Student record is created and linked back here.
          </CardDescription>
          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Section</span>
            <select
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            >
              <option value="">Select section</option>
              {classes.data?.map((c) => (
                <optgroup key={c.id} label={c.name}>
                  {c.sections.map((s) => (
                    <option key={s.id} value={s.id}>{c.name} — {s.name}</option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
          <div className="mt-3 flex justify-end">
            <Button onClick={() => enroll.mutate()} loading={enroll.isPending}
                    disabled={!sectionId} variant="accent">
              Enrol student
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
}

// ============================================================
// Helpers
// ============================================================

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wide text-slate-500">{label}</div>
      <div className="font-medium text-slate-800 mt-0.5">{value}</div>
    </div>
  );
}

function StatusBadge({ status, size = 'sm' }: { status: AdmissionStatus; size?: 'sm' | 'md' }) {
  const map: Record<AdmissionStatus, 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info'> = {
    ENQUIRY: 'neutral',
    APPLICATION_SUBMITTED: 'info',
    TEST_SCHEDULED: 'warning',
    TEST_COMPLETED: 'warning',
    OFFERED: 'primary',
    ACCEPTED: 'accent',
    DECLINED: 'danger',
    ENROLLED: 'success',
    WITHDRAWN: 'neutral',
    REJECTED: 'danger',
  };
  return <Badge tone={map[status]} size={size}>{status.replace(/_/g, ' ').toLowerCase()}</Badge>;
}
