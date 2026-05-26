'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { UseQueryResult, useMutation, useQueries, useQueryClient } from '@tanstack/react-query';
import type { AdmissionsPage } from '@/api/endpoints/admissions';
import { Plus, ArrowRight, GraduationCap, Phone, Mail, Calendar } from 'lucide-react';
import { admissionsApi } from '@/api/endpoints/admissions';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Card, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { isApiError, hasCode } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';
import type { AdmissionResponse, AdmissionStatus, EnquiryRequest } from '@/types/domain';

/**
 * Kanban board over the AdmissionStatus state machine. Each column polls a
 * status-filtered slice of /admissions. Mutating the funnel — schedule test,
 * make offer, accept, decline, reject, enroll — updates and invalidates the
 * relevant columns.
 *
 * The five visible columns map to the most-used states; terminal states are
 * folded into "Closed" so the board stays focused on actionable items.
 */

interface Column {
  key: AdmissionStatus;
  label: string;
  tone: 'neutral' | 'info' | 'warning' | 'primary' | 'success';
  description: string;
}

const COLUMNS: Column[] = [
  { key: 'ENQUIRY',               label: 'Enquiry',     tone: 'neutral', description: 'New leads' },
  { key: 'APPLICATION_SUBMITTED', label: 'Application', tone: 'info',    description: 'Full forms in' },
  { key: 'TEST_SCHEDULED',        label: 'Test',        tone: 'warning', description: 'Awaiting test' },
  { key: 'OFFERED',               label: 'Offered',     tone: 'primary', description: 'Awaiting response' },
  { key: 'ENROLLED',              label: 'Enrolled',    tone: 'success', description: 'Joined' },
];

export default function AdmissionsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const [createOpen, setCreateOpen] = useState(false);

  // Run one query per column — parallel, independent revalidation.
  const queries = useQueries({
    queries: COLUMNS.map((c) => ({
      queryKey: ['admissions', tenantId, c.key],
      queryFn: () => admissionsApi.list(tenantId, { status: c.key, size: 50 }),
      enabled: !!tenantId,
      retry: false,
    })),
  });

  const firstError = queries.find((q) => q.isError);
  if (firstError?.error && hasCode(firstError.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader title="Admissions" description="Enquiry → application → test → offer → enrolled" icon={<GraduationCap size={18} />} />
        <EmptyState
          icon={<GraduationCap size={28} />}
          title="Admissions funnel is part of the Enterprise plan"
          description="Capture leads from your website, run entrance tests, issue offer letters and convert students to enrolment — all in one funnel."
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Admissions"
        description="Manage every applicant from first enquiry to enrolment"
        icon={<GraduationCap size={18} />}
        actions={
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button onClick={() => setCreateOpen(true)}>
              <Plus size={14} /> New enquiry
            </Button>
          </RequireRole>
        }
      />

      {firstError && !hasCode(firstError.error, 'FEATURE_DISABLED') && (
        <ErrorBanner error={firstError.error} onRetry={() => queries.forEach((q) => q.refetch())} />
      )}

      {/* Kanban board — horizontally scrollable on narrow screens */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        {COLUMNS.map((col, idx) => (
          <ColumnView
            key={col.key}
            tenantId={tenantId}
            column={col}
            query={queries[idx]!}
          />
        ))}
      </div>

      <CreateEnquiryModal
        tenantId={tenantId}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
    </div>
  );
}

// ------------------------- Column -------------------------

function ColumnView({ tenantId, column, query }: {
  tenantId: string;
  column: Column;
  query: UseQueryResult<AdmissionsPage, unknown>;
}) {
  const data = query.data?.items ?? [];
  return (
    <div className="flex flex-col min-h-[200px]">
      <div className="flex items-center justify-between mb-2 px-0.5">
        <div className="flex items-center gap-2">
          <Badge tone={column.tone} size="sm" dot>{column.label}</Badge>
          <span className="text-xs text-slate-400 tabular-nums">{data.length}</span>
        </div>
        <span className="text-[10px] text-slate-400 uppercase tracking-wide">{column.description}</span>
      </div>

      <div className="flex-1 bg-slate-100/60 rounded-brand p-2 space-y-2 overflow-y-auto">
        {query.isLoading && <Skeleton className="h-24 w-full" />}
        {!query.isLoading && data.length === 0 && (
          <div className="text-center text-xs text-slate-400 py-8">No items</div>
        )}
        {data.map((a) => (
          <AdmissionCard key={a.id} tenantId={tenantId} admission={a} />
        ))}
      </div>
    </div>
  );
}

// ------------------------- Card -------------------------

function AdmissionCard({ tenantId, admission }: { tenantId: string; admission: AdmissionResponse }) {
  const qc = useQueryClient();
  const toast = useToast();

  function invalidateAllColumns() {
    qc.invalidateQueries({ queryKey: ['admissions', tenantId] });
  }

  /**
   * Transitions that need user input (test schedule date, marks, offer terms, accept confirmation)
   * route to the detail page where the proper form lives. Inline mutate only for transitions
   * that are pure status flips with no data corruption risk.
   *
   * Audit fixed: previously the kanban auto-scheduled tests 7 days out, posted 0/100 marks
   * with "Pending grading", and accepted offers — all on a single misclick.
   */
  const REQUIRES_FORM: Record<string, true> = {
    APPLICATION_SUBMITTED: true,  // schedule test — needs date + venue
    TEST_SCHEDULED:        true,  // record result — needs marks
    OFFERED:               true,  // accept/decline — terminal, needs confirm
  };
  const needsForm = REQUIRES_FORM[admission.status] === true;

  const advance = useMutation({
    mutationFn: async () => {
      switch (admission.status) {
        case 'ENQUIRY':
          return admissionsApi.submitApplication(tenantId, admission.id);
        case 'TEST_COMPLETED':
          return admissionsApi.makeOffer(tenantId, admission.id, undefined);
        default:
          return admission;
      }
    },
    onSuccess: () => {
      invalidateAllColumns();
      toast.success('Moved to next stage');
    },
    onError: (e) => {
      toast.error(isApiError(e) ? e.message : 'Could not advance');
    },
  });

  const reject = useMutation({
    mutationFn: () => admissionsApi.reject(tenantId, admission.id, 'Rejected from board'),
    onSuccess: () => {
      invalidateAllColumns();
      toast.info('Marked as rejected');
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not reject'),
  });

  const isTerminal = ['ENROLLED', 'DECLINED', 'WITHDRAWN', 'REJECTED'].includes(admission.status);
  const nextLabel = nextActionLabel(admission.status);

  return (
    <Card className="p-3 group hover:shadow-sm hover:border-primary transition-all" padding="none">
      <div className="space-y-2">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <Link href={`/tenants/${tenantId}/admissions/${admission.id}`}
                  className="text-sm font-semibold text-slate-900 hover:text-primary truncate block">
              {admission.studentDisplayName}
            </Link>
            <div className="text-[11px] text-slate-500 mt-0.5">
              {admission.intendedClass}{admission.intendedSection ? ` · ${admission.intendedSection}` : ''}
              {admission.intendedAcademicYear ? ` · ${admission.intendedAcademicYear}` : ''}
            </div>
          </div>
        </div>

        {/* Parent contact strip */}
        <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
          <span className="inline-flex items-center gap-1 truncate">
            <Phone size={10} className="shrink-0" /> {admission.parentPhone}
          </span>
          {admission.parentEmail && (
            <span className="inline-flex items-center gap-1 truncate">
              <Mail size={10} className="shrink-0" /> {admission.parentEmail}
            </span>
          )}
        </div>

        {admission.testScheduledAt && admission.status === 'TEST_SCHEDULED' && (
          <div className="flex items-center gap-1 text-[11px] text-warning bg-warning/10 rounded px-1.5 py-0.5">
            <Calendar size={10} /> {new Date(admission.testScheduledAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
          </div>
        )}

        {nextLabel && !isTerminal && (
          <RequireRole roles={OWNER_OR_ADMIN}>
            <div className="flex items-center gap-1 pt-1.5 border-t border-slate-100 opacity-0 group-hover:opacity-100 transition-opacity">
              {needsForm ? (
                <Link
                  href={`/tenants/${tenantId}/admissions/${admission.id}`}
                  className="flex-1 inline-flex items-center justify-center gap-1 text-xs rounded px-2 py-1 border border-slate-200 hover:bg-slate-50"
                >
                  {nextLabel} <ArrowRight size={11} />
                </Link>
              ) : (
                <Button size="xs" variant="subtle" onClick={() => advance.mutate()} loading={advance.isPending} className="flex-1">
                  {nextLabel} <ArrowRight size={11} />
                </Button>
              )}
              {admission.status !== 'OFFERED' && (
                <Button size="xs" variant="ghost" onClick={() => reject.mutate()} loading={reject.isPending} aria-label="Reject">
                  ✕
                </Button>
              )}
            </div>
          </RequireRole>
        )}
      </div>
    </Card>
  );
}

function nextActionLabel(s: AdmissionStatus): string | null {
  switch (s) {
    case 'ENQUIRY':               return 'Move to application';
    case 'APPLICATION_SUBMITTED': return 'Schedule test';
    case 'TEST_SCHEDULED':        return 'Record result';
    case 'TEST_COMPLETED':        return 'Make offer';
    case 'OFFERED':               return 'Accept';
    default:                       return null;
  }
}

// ------------------------- Create modal -------------------------

function CreateEnquiryModal({ tenantId, open, onClose }: {
  tenantId: string; open: boolean; onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState<EnquiryRequest>({
    parentPhone: '',
    studentFirstName: '',
    intendedClass: '',
  });

  const create = useMutation({
    mutationFn: () => admissionsApi.create(tenantId, form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admissions', tenantId] });
      toast.success('Enquiry recorded');
      onClose();
      setForm({ parentPhone: '', studentFirstName: '', intendedClass: '' });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not create enquiry'),
  });

  return (
    <Modal open={open} onClose={onClose} title="New admission enquiry">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Input label="Student first name" required value={form.studentFirstName}
            onChange={(e) => setForm({ ...form, studentFirstName: e.target.value })} />
          <Input label="Student last name" value={form.studentLastName ?? ''}
            onChange={(e) => setForm({ ...form, studentLastName: e.target.value })} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Input label="Intended class" required placeholder="e.g. Class 5" value={form.intendedClass}
            onChange={(e) => setForm({ ...form, intendedClass: e.target.value })} />
          <Input label="Section preference" value={form.intendedSection ?? ''}
            onChange={(e) => setForm({ ...form, intendedSection: e.target.value })} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Input label="Parent / guardian name" value={form.parentName ?? ''}
            onChange={(e) => setForm({ ...form, parentName: e.target.value })} />
          <Input label="Parent phone" type="tel" required value={form.parentPhone}
            onChange={(e) => setForm({ ...form, parentPhone: e.target.value })} />
        </div>
        <Input label="Parent email (optional)" type="email" value={form.parentEmail ?? ''}
          onChange={(e) => setForm({ ...form, parentEmail: e.target.value })} />
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Source (optional)</span>
          <select className="mt-1 block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={form.source ?? ''}
            onChange={(e) => setForm({ ...form, source: e.target.value || undefined })}>
            <option value="">— select —</option>
            <option value="WEBSITE">Website</option>
            <option value="WALK_IN">Walk-in</option>
            <option value="REFERRAL">Referral</option>
            <option value="AD">Advertisement</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Notes</span>
          <textarea
            rows={2}
            value={form.notes ?? ''}
            onChange={(e) => setForm({ ...form, notes: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            placeholder="Anything else worth recording…"
          />
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" loading={create.isPending}
            disabled={!form.studentFirstName || !form.parentPhone || !form.intendedClass}>
            Create enquiry
          </Button>
        </div>
      </form>
    </Modal>
  );
}
