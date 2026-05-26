'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Plus, Check, X, Plane, AlertCircle, ChevronDown, Calendar, MessageSquare,
} from 'lucide-react';
import { hrApi } from '@/api/endpoints/hr';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';
import type {
  LeaveApplicationRequest, LeaveApplicationResponse, LeaveStatus, LeaveType,
} from '@/types/domain';

const LEAVE_TYPES: LeaveType[] = ['CASUAL', 'SICK', 'EARNED', 'UNPAID', 'MATERNITY', 'PATERNITY', 'COMP_OFF', 'OTHER'];

const STATUS_TONE: Record<LeaveStatus, 'neutral' | 'success' | 'warning' | 'danger' | 'info'> = {
  SUBMITTED: 'info',
  APPROVED:  'success',
  REJECTED:  'danger',
  CANCELLED: 'neutral',
};

export default function LeavePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [applyOpen, setApplyOpen] = useState(false);
  const [filterStaffId, setFilterStaffId] = useState<string>('');

  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
  });

  const pendingQ = useQuery({
    queryKey: ['leave', tenantId, 'pending'],
    queryFn: () => hrApi.pendingLeaves(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  const filteredQ = useQuery({
    queryKey: ['leave', tenantId, 'staff', filterStaffId],
    queryFn: () => hrApi.staffLeaves(tenantId, filterStaffId),
    enabled: !!tenantId && !!filterStaffId,
    retry: false,
  });

  const staffById = useMemo(() => {
    const m = new Map<string, string>();
    for (const s of staffQ.data ?? []) m.set(s.id ?? '', s.displayName);
    return m;
  }, [staffQ.data]);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['leave', tenantId] });
  };

  const decide = useMutation({
    mutationFn: ({ id, approve, note }: { id: string; approve: boolean; note?: string }) =>
      hrApi.decideLeave(tenantId, id, { approve, note }),
    onSuccess: (_, vars) => {
      toast.success(vars.approve ? 'Leave approved' : 'Leave rejected');
      invalidate();
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Action failed'),
  });

  const cancel = useMutation({
    mutationFn: (id: string) => hrApi.cancelLeave(tenantId, id),
    onSuccess: () => { toast.info('Leave cancelled'); invalidate(); },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Action failed'),
  });

  if (pendingQ.isError && hasCode(pendingQ.error, 'FEATURE_DISABLED')) {
    return (
      <EmptyState
        icon={<Plane size={28} />}
        title="Leave management isn't enabled for your plan"
        description="Submit, approve and track staff leave with automatic balance updates."
      />
    );
  }

  return (
    <div className="space-y-5">
      {/* Toolbar */}
      <div className="flex flex-wrap items-end gap-3">
        <label className="block flex-1 max-w-xs">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">View applications for</span>
          <select
            value={filterStaffId}
            onChange={(e) => setFilterStaffId(e.target.value)}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">— Show pending approvals only —</option>
            {(staffQ.data ?? []).filter((s) => s.id).map((s) => (
              <option key={s.id} value={s.id!}>{s.displayName}</option>
            ))}
          </select>
        </label>

        <Button onClick={() => setApplyOpen(true)} className="ml-auto">
          <Plus size={14} /> Apply for leave
        </Button>
      </div>

      {/* Pending approvals */}
      {!filterStaffId && (
        <Card padding="none" className="overflow-hidden">
          <CardHeader className="flex items-center justify-between">
            <div>
              <CardTitle>Pending approvals</CardTitle>
              <CardDescription>
                Decisions go straight to the staff member's leave balance once approved
              </CardDescription>
            </div>
            <Badge tone="info">{pendingQ.data?.length ?? 0}</Badge>
          </CardHeader>

          {pendingQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
          {pendingQ.isError && !hasCode(pendingQ.error, 'FEATURE_DISABLED') && (
            <CardBody>
              <ErrorBanner error={pendingQ.error} onRetry={() => pendingQ.refetch()} />
            </CardBody>
          )}

          {pendingQ.data && pendingQ.data.length === 0 && (
            <CardBody>
              <div className="text-center py-8 text-sm text-slate-500">
                <Check size={28} className="mx-auto mb-2 text-success" />
                No pending leave applications.
              </div>
            </CardBody>
          )}

          {pendingQ.data && pendingQ.data.length > 0 && (
            <ul className="divide-y divide-slate-100">
              {pendingQ.data.map((leave) => (
                <PendingRow
                  key={leave.id}
                  leave={leave}
                  staffName={staffById.get(leave.staffId) ?? '—'}
                  onApprove={(note) => decide.mutate({ id: leave.id, approve: true, note })}
                  onReject={(note) => decide.mutate({ id: leave.id, approve: false, note })}
                  busy={decide.isPending}
                />
              ))}
            </ul>
          )}
        </Card>
      )}

      {/* Per-staff history */}
      {filterStaffId && (
        <Card padding="none" className="overflow-hidden">
          <CardHeader>
            <CardTitle>{staffById.get(filterStaffId) ?? 'Staff'} — leave history</CardTitle>
            <CardDescription>All applications, including cancelled and rejected</CardDescription>
          </CardHeader>

          {filteredQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
          {filteredQ.data && filteredQ.data.length === 0 && (
            <CardBody><p className="text-sm text-slate-500 py-6 text-center">No leave applications.</p></CardBody>
          )}

          {filteredQ.data && filteredQ.data.length > 0 && (
            <ul className="divide-y divide-slate-100">
              {filteredQ.data.map((leave) => (
                <HistoryRow key={leave.id} leave={leave}
                  onCancel={() => cancel.mutate(leave.id)} canCancel={leave.status === 'SUBMITTED'} />
              ))}
            </ul>
          )}
        </Card>
      )}

      <ApplyLeaveModal
        open={applyOpen}
        onClose={() => setApplyOpen(false)}
        tenantId={tenantId}
        staff={staffQ.data ?? []}
        onSuccess={() => invalidate()}
      />
    </div>
  );
}

// ============================================================
// Rows
// ============================================================

function PendingRow({
  leave, staffName, onApprove, onReject, busy,
}: {
  leave: LeaveApplicationResponse;
  staffName: string;
  onApprove: (note?: string) => void;
  onReject: (note?: string) => void;
  busy: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const [note, setNote] = useState('');

  return (
    <li className="p-5">
      <div className="flex flex-wrap items-start gap-3">
        <div className="w-9 h-9 rounded-full bg-brand-gradient text-primary-fg grid place-items-center text-sm font-semibold shrink-0">
          {staffName.charAt(0).toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-slate-900">{staffName}</span>
            <Badge tone="primary" size="sm">{prettyLeaveType(leave.leaveType)}</Badge>
            <span className="text-[11px] text-slate-500 tabular-nums">{leave.days} day{leave.days !== 1 ? 's' : ''}</span>
          </div>
          <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-1">
            <Calendar size={11} />
            {new Date(leave.startDate).toLocaleDateString('en-IN')}
            {leave.endDate !== leave.startDate && ` → ${new Date(leave.endDate).toLocaleDateString('en-IN')}`}
          </div>
          {leave.reason && (
            <p className="text-sm text-slate-700 mt-2 italic">"{leave.reason}"</p>
          )}
        </div>

        <RequireRole roles={OWNER_OR_ADMIN}>
          <div className="flex gap-2">
            <Button variant="accent" size="sm"
                    onClick={() => onApprove(expanded ? note : undefined)} loading={busy}>
              <Check size={14} /> Approve
            </Button>
            <Button variant="danger" size="sm"
                    onClick={() => onReject(expanded ? note : undefined)} loading={busy}>
              <X size={14} /> Reject
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setExpanded((e) => !e)}>
              <ChevronDown size={14} className={cn('transition', expanded && 'rotate-180')} />
            </Button>
          </div>
        </RequireRole>
      </div>

      {expanded && (
        <div className="mt-3 ml-12">
          <label className="block">
            <span className="text-[10px] uppercase tracking-wide text-slate-500 flex items-center gap-1">
              <MessageSquare size={10} /> Decision note (optional)
            </span>
            <textarea
              rows={2}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className="mt-1 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
              placeholder="Add a note explaining your decision"
            />
          </label>
        </div>
      )}
    </li>
  );
}

function HistoryRow({
  leave, onCancel, canCancel,
}: {
  leave: LeaveApplicationResponse;
  onCancel: () => void;
  canCancel: boolean;
}) {
  return (
    <li className="px-5 py-3 flex items-center justify-between gap-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 flex-wrap">
          <Badge tone="primary" size="sm">{prettyLeaveType(leave.leaveType)}</Badge>
          <Badge tone={STATUS_TONE[leave.status]} size="sm" dot>{leave.status.toLowerCase()}</Badge>
          <span className="text-[11px] text-slate-500 tabular-nums">{leave.days} day{leave.days !== 1 ? 's' : ''}</span>
        </div>
        <div className="text-xs text-slate-500 mt-0.5">
          {new Date(leave.startDate).toLocaleDateString('en-IN')}
          {leave.endDate !== leave.startDate && ` → ${new Date(leave.endDate).toLocaleDateString('en-IN')}`}
          {leave.decisionNote && (
            <span className="ml-1 text-slate-600 italic">· "{leave.decisionNote}"</span>
          )}
        </div>
      </div>
      {canCancel && (
        <Button variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
      )}
    </li>
  );
}

function prettyLeaveType(t: LeaveType): string {
  return t.replace(/_/g, ' ').toLowerCase();
}

// ============================================================
// Apply-for-leave modal
// ============================================================

function ApplyLeaveModal({
  open, onClose, tenantId, staff, onSuccess,
}: {
  open: boolean; onClose: () => void; tenantId: string;
  staff: Array<{ id?: string | null; displayName: string; active: boolean }>;
  onSuccess: () => void;
}) {
  const toast = useToast();
  const initial: LeaveApplicationRequest = {
    staffId: '', leaveType: 'CASUAL', startDate: new Date().toISOString().slice(0, 10),
    endDate: new Date().toISOString().slice(0, 10), days: 1, reason: '',
  };
  const [form, setForm] = useState(initial);

  const apply = useMutation({
    mutationFn: () => hrApi.submitLeave(tenantId, form),
    onSuccess: () => {
      toast.success('Leave application submitted');
      onSuccess();
      onClose();
      setForm(initial);
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not submit'),
  });

  // Auto-compute days when start/end change (inclusive whole-day count).
  const onDateChange = (field: 'startDate' | 'endDate', value: string) => {
    const next = { ...form, [field]: value };
    const s = new Date(next.startDate);
    const e = new Date(next.endDate);
    if (!isNaN(s.getTime()) && !isNaN(e.getTime()) && e >= s) {
      next.days = Math.floor((e.getTime() - s.getTime()) / (1000 * 60 * 60 * 24)) + 1;
    }
    setForm(next);
  };

  return (
    <Modal open={open} onClose={onClose} title="Apply for leave">
      <form onSubmit={(e) => { e.preventDefault(); apply.mutate(); }} className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Staff</span>
          <select
            required
            value={form.staffId}
            onChange={(e) => setForm({ ...form, staffId: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">Select staff member</option>
            {staff.filter((s) => s.active && s.id).map((s) => (
              <option key={s.id} value={s.id!}>{s.displayName}</option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Leave type</span>
          <select
            value={form.leaveType}
            onChange={(e) => setForm({ ...form, leaveType: e.target.value as LeaveType })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            {LEAVE_TYPES.map((t) => (
              <option key={t} value={t}>{prettyLeaveType(t)}</option>
            ))}
          </select>
        </label>

        <div className="grid grid-cols-2 gap-3">
          <Input type="date" label="Start date" required value={form.startDate}
                 onChange={(e) => onDateChange('startDate', e.target.value)} />
          <Input type="date" label="End date" required value={form.endDate}
                 min={form.startDate}
                 onChange={(e) => onDateChange('endDate', e.target.value)} />
        </div>

        <Input type="number" label="Days" step="0.5" min="0.5" required
               value={form.days}
               onChange={(e) => setForm({ ...form, days: Number(e.target.value) })} />

        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Reason</span>
          <textarea
            rows={2}
            value={form.reason ?? ''}
            onChange={(e) => setForm({ ...form, reason: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            placeholder="Add a short explanation"
          />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={apply.isPending}
                  disabled={!form.staffId || !form.startDate || !form.endDate || form.days <= 0}>
            Submit
          </Button>
        </div>
      </form>
    </Modal>
  );
}
